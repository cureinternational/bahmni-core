# Batch Forms Data Endpoint Proposal

**Action**: 105551 - Care Instructions Refactoring
**Context**: Optimization opportunity to reduce API calls when loading care instructions for multiple ward patients

---

## Problem Statement

The current form data API only supports single-patient queries:
- Endpoint: `BAHMNI_CORE + "/patient/{patientUuid}/forms"`
- For a ward with 10 patients, CareView makes 10 separate API calls to fetch form data
- Each call: `GET /patient/{patientUuid}/forms?visitUuid={visitUuid}&formType=v2`

This proposal introduces a **batch endpoint** to fetch form data for multiple patients in a single request, following the existing pattern used by slots and tasks APIs.

---

## Proposed Endpoint

### Endpoint Definition

```
GET /openmrs/ws/rest/v1/bahmnicore/patients/forms
```

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `patientUuids` | string (repeating) | Yes | Patient UUIDs to fetch forms for. Use multiple query params: `?patientUuids=uuid1&patientUuids=uuid2&...` |
| `visitUuid` | string | Yes | Visit UUID for form filtering |
| `formType` | string | No | Form type filter (e.g., 'v2'). Defaults to all types if omitted |

### Request Example

```
GET /openmrs/ws/rest/v1/bahmnicore/patients/forms?patientUuids=uuid1&patientUuids=uuid2&patientUuids=uuid3&visitUuid=visit-uuid&formType=v2
```

### Response Format

Return an object mapping patient UUIDs to their form data arrays:

```json
{
  "uuid1": [
    {
      "formType": "v2",
      "formName": "Doctor Patient Progress Notes",
      "formVersion": 1,
      "visitUuid": "visit-uuid-1",
      "visitStartDateTime": 1713875236000,
      "encounterUuid": "encounter-uuid-1",
      "encounterDateTime": 1713955252000,
      "providers": [{"providerName": "Dr. Smith", "uuid": "provider-uuid-1"}]
    }
  ],
  "uuid2": [
    {
      "formType": "v2",
      "formName": "Patient Progress Notes and Orders",
      "formVersion": 1,
      "visitUuid": "visit-uuid-1",
      "visitStartDateTime": 1713875236000,
      "encounterUuid": "encounter-uuid-2",
      "encounterDateTime": 1713941600000,
      "providers": [{"providerName": "Dr. Jones", "uuid": "provider-uuid-2"}]
    }
  ]
}
```

---

## Implementation Notes

### Backend (OpenMRS Module)

1. **Endpoint Handler**: Create a REST endpoint that:
   - Accepts multiple `patientUuids` query parameters
   - Calls the existing single-patient form fetch logic in a loop
   - Aggregates results into the response object
   - Returns structured data (same format as current `/patient/{patientUuid}/forms`)

2. **Performance Considerations**:
   - Consider database query optimization for batch retrieval
   - Potential to refactor database calls into a single batch query if available
   - Cache form data temporarily if the same visit is queried multiple times in short succession

3. **Authorization**:
   - Apply same authorization checks as current endpoint (per-patient access control)
   - Ensure user can access all requested patients

### Frontend (This Repository)

1. **Add to constants.js**:
   ```javascript
   export const GET_FORMS_FOR_PATIENTS_URL = BAHMNI_CORE + "/patients/forms";
   ```

2. **Create utility function** in `src/features/CareViewSummary/utils/CareViewSummary.js`:
   ```javascript
   export const getFormsForPatients = async (patientUuids, visitUuid) => {
     const patientUuidParams = patientUuids
       .map((uuid) => `patientUuids=${uuid}`)
       .join("&");
     const FORMS_URL = `${GET_FORMS_FOR_PATIENTS_URL}?${patientUuidParams}&visitUuid=${visitUuid}&formType=v2`;

     try {
       const response = await axios.get(FORMS_URL, { withCredentials: true });
       if (response.status === 200) {
         return response.data; // Object: {patientUuid: [forms...], ...}
       }
       return {};
     } catch (error) {
       console.error("Error fetching forms for patients:", error);
       return {};
     }
   };
   ```

3. **Update CareViewPatientsSummary** to call batch endpoint instead of per-patient:
   ```javascript
   // Before: Loop through patients calling fetchFormData per patient
   // After: Call getFormsForPatients once with all patient UUIDs

   const patientUuids = patientsSummary.map(p => p.uuid);
   const formsData = await getFormsForPatients(patientUuids, visitUuid);
   // formsData is now {uuid1: [...forms], uuid2: [...forms], ...}
   ```

---

## Benefits

| Metric | Before | After |
|--------|--------|-------|
| API Calls (10 patients) | 10 | 1 |
| API Calls (20 patients) | 20 | 1 |
| Network Overhead | High | Low |
| Parallel Processing | N/A | Possible (batch query) |

---

## Migration Path

1. **Phase 1** (Backend): Implement batch endpoint in OpenMRS module
2. **Phase 2** (Frontend): Add `getFormsForPatients` utility function
3. **Phase 3** (Frontend): Update `CareViewPatientsSummary` to use batch endpoint
4. **Phase 4** (Testing): Validate form data consistency and performance

**Backwards Compatibility**: The single-patient endpoint remains unchanged, so existing code continues to work.

---

## Fallback Strategy

If batch endpoint is not available:
- Current implementation (per-patient fetching) continues to work
- `loadCareInstructionsForPatient` gracefully handles both scenarios
- No breaking changes

---

## Similar Endpoints in Codebase

This proposal follows the established pattern used by existing batch APIs:
- `GET /ipd/schedule/type/medication/patientsMedicationSummary?patientUuids=...`
- `GET /tasks?patientUuids=...&startTime=...&endTime=...`

---

## Timeline

- **Proposed Implementation**: Next sprint (dependent on backend team availability)
- **Current Status**: Using on-demand per-patient approach (Option A) - stable and working
- **Future Consideration**: Implement batch endpoint when backend resources available

