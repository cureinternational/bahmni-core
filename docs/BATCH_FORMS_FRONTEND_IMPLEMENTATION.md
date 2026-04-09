# Batch Forms Endpoint - Frontend Implementation

This document shows the code changes needed once the batch forms endpoint is available.

---

## 1. Add Constant

**File**: `src/constants.js`

```javascript
// Around line 100, after GET_SLOTS_FOR_PATIENTS_URL
export const GET_FORMS_FOR_PATIENTS_URL = BAHMNI_CORE + "/patients/forms";
```

---

## 2. Create Utility Function

**File**: `src/features/CareViewSummary/utils/CareViewSummary.js`

Add this function after `getTasksForPatients`:

```javascript
import { GET_FORMS_FOR_PATIENTS_URL } from "../../../constants";

export const getFormsForPatients = async (patientUuids, visitUuid) => {
  if (!patientUuids || patientUuids.length === 0) {
    return {};
  }

  try {
    const patientUuidParams = patientUuids
      .map((uuid) => `patientUuids=${uuid}`)
      .join("&");
    const FORMS_URL = `${GET_FORMS_FOR_PATIENTS_URL}?${patientUuidParams}&visitUuid=${visitUuid}&formType=v2`;

    const response = await axios.get(FORMS_URL, { withCredentials: true });

    if (response.status === 200) {
      return response.data; // {patientUuid: [forms...], ...}
    }
    return {};
  } catch (error) {
    console.error("Error fetching forms for patients:", error);
    return {};
  }
};
```

---

## 3. Update CareViewPatientsSummary Component

**File**: `src/features/CareViewPatientsSummary/components/CareViewPatientsSummary.jsx`

### Current Implementation (Per-Patient Fetching)

```javascript
// Current: Loops through each patient individually
const fetchCareInstructions = async () => {
  const careInstructionsMap = {};

  for (const patient of patientsSummary) {
    try {
      const instructions = await loadCareInstructionsForPatient(
        patient.uuid,
        patient.visitDetails?.uuid,
        ciConfig?.formConcepts
        // No allFormsFilledInCurrentVisit - fetches on demand per patient
      );
      careInstructionsMap[patient.uuid] = instructions;
    } catch (error) {
      console.error(`Error loading care instructions for ${patient.uuid}:`, error);
      careInstructionsMap[patient.uuid] = [];
    }
  }

  setCareInstructions(careInstructionsMap);
};
```

### Optimized Implementation (Batch Fetching)

```javascript
import { getFormsForPatients } from "../../CareViewSummary/utils/CareViewSummary";

// Updated: Fetch all forms in one batch call
const fetchCareInstructions = async () => {
  if (!patientsSummary || patientsSummary.length === 0) {
    setCareInstructions({});
    return;
  }

  try {
    // Get common visitUuid (assumes all patients in current ward visit)
    const visitUuid = patientsSummary[0]?.visitDetails?.uuid;
    if (!visitUuid) {
      setCareInstructions({});
      return;
    }

    // Fetch forms for ALL patients in one batch call
    const patientUuids = patientsSummary.map((p) => p.uuid);
    const formsDataMap = await getFormsForPatients(patientUuids, visitUuid);

    // Process forms for each patient
    const careInstructionsMap = {};

    for (const patient of patientsSummary) {
      try {
        const allFormsForPatient = formsDataMap[patient.uuid] || [];

        const instructions = await loadCareInstructionsForPatient(
          null, // patientUuid - not needed since we have form data
          null, // visitUuid - not needed since we have form data
          ciConfig?.formConcepts,
          allFormsForPatient // Pass pre-loaded form data
        );
        careInstructionsMap[patient.uuid] = instructions;
      } catch (error) {
        console.error(`Error processing care instructions for ${patient.uuid}:`, error);
        careInstructionsMap[patient.uuid] = [];
      }
    }

    setCareInstructions(careInstructionsMap);
  } catch (error) {
    console.error("Error fetching care instructions:", error);
    setCareInstructions({});
  }
};

// In useEffect hook - call this function
useEffect(() => {
  if (patientsSummary && patientsSummary.length > 0 && ciConfig) {
    fetchCareInstructions();
  }
}, [patientsSummary, ciConfig]);
```

---

## 4. Performance Comparison

### Before Batch Endpoint

```
Ward with 10 patients:
1. Load patient list
2. For each patient (10 iterations):
   - Call loadCareInstructionsForPatient(patientUuid, visitUuid, formConcepts)
   - Which internally calls fetchFormData(patientUuid, visitUuid)

Total API Calls: 10 form requests
Timeline: Sequential or parallel (if Promise.all used)
```

### After Batch Endpoint

```
Ward with 10 patients:
1. Load patient list
2. Call getFormsForPatients([uuid1, uuid2, ..., uuid10], visitUuid)
   - Returns: {uuid1: [...forms], uuid2: [...forms], ...}
3. For each patient (10 iterations):
   - Call loadCareInstructionsForPatient(null, null, formConcepts, formsData[uuid])

Total API Calls: 1 batch form request + per-patient processing
Timeline: Single batch call + local processing
```

**Result**: 90% reduction in API calls for a 10-patient ward.

---

## 5. Backward Compatibility

The implementation maintains full backward compatibility:

```javascript
// loadCareInstructionsForPatient handles both scenarios:

// Scenario A: Pre-loaded form data (from batch endpoint)
const instructions = await loadCareInstructionsForPatient(
  null, null, formConcepts, preLoadedForms
);

// Scenario B: On-demand fetching (current approach)
const instructions = await loadCareInstructionsForPatient(
  patientUuid, visitUuid, formConcepts
);
```

**No changes needed to `loadCareInstructionsForPatient` logic.**

---

## 6. Testing Strategy

### Unit Tests

```javascript
describe("getFormsForPatients", () => {
  it("should fetch forms for multiple patients", async () => {
    const patientUuids = ["uuid1", "uuid2"];
    const visitUuid = "visit-uuid";

    const mockResponse = {
      uuid1: [{formName: "Form1", ...}],
      uuid2: [{formName: "Form2", ...}]
    };

    mockAxios.onGet(new RegExp(".*patients/forms.*")).reply(200, mockResponse);

    const result = await getFormsForPatients(patientUuids, visitUuid);

    expect(result).toEqual(mockResponse);
  });

  it("should return empty object when no patient UUIDs provided", async () => {
    const result = await getFormsForPatients([], "visit-uuid");
    expect(result).toEqual({});
  });

  it("should handle API errors gracefully", async () => {
    mockAxios.onGet(new RegExp(".*patients/forms.*")).reply(500);

    const result = await getFormsForPatients(["uuid1"], "visit-uuid");
    expect(result).toEqual({});
  });
});
```

### Integration Tests

```javascript
describe("CareViewPatientsSummary - Batch Forms", () => {
  it("should load care instructions for multiple patients via batch endpoint", async () => {
    const mockFormsData = {
      "uuid1": [{formName: "Form1", ...}],
      "uuid2": [{formName: "Form2", ...}]
    };

    mockGetFormsForPatients.mockResolvedValue(mockFormsData);

    render(
      <CareViewPatientsSummary
        patientsSummary={mockPatientsList}
        ciConfig={mockCIConfig}
      />
    );

    await waitFor(() => {
      expect(mockGetFormsForPatients).toHaveBeenCalledWith(
        ["uuid1", "uuid2"],
        "visit-uuid"
      );
    });
  });
});
```

---

## 7. Migration Checklist

- [ ] Backend team implements batch forms endpoint
- [ ] Add `GET_FORMS_FOR_PATIENTS_URL` constant
- [ ] Add `getFormsForPatients` utility function to CareViewSummary
- [ ] Update CareViewPatientsSummary to use batch endpoint
- [ ] Update unit tests to mock batch endpoint
- [ ] Manual testing: Verify form data loads correctly
- [ ] Performance testing: Measure reduction in API calls
- [ ] Deploy to staging and verify with real data
- [ ] Monitor performance metrics post-deployment

---

## 8. Rollback Plan

If the batch endpoint has issues:
1. Revert `CareViewPatientsSummary` to use per-patient `loadCareInstructionsForPatient`
2. Keep batch endpoint code in place but don't call it
3. Forms will load via existing `fetchFormData` path

**Impact**: Zero - existing code path still works, just slower (N API calls instead of 1).

---

## 9. Future Enhancements

Once batch endpoint is stable, consider:
1. **Caching**: Cache form data for the current visit to avoid refetching
2. **Incremental Loading**: Load forms for visible patients first, then background-load others
3. **Real-time Sync**: Use WebSocket to push form updates instead of polling
4. **Compression**: Compress batch response if form data size is large

