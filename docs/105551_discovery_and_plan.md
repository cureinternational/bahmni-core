# Discovery & Plan: 105551

**Hive Action**: 105551
**Title**: Displaying new care instructions in the IPD care view
**Story Points**: 2
**Created**: 2026-04-07

---

## Hive Action Details

### Description
As an IPD nurse, I want to see care instructions in the IPD care view so that I can go and create tasks for the care instructions in the IPD dashboard.

**Business context**: Story #104256 separated pending and new tasks. Currently only medication tasks appear as "new." This story introduces "new care instructions" notifications in the IPD Care View dashboard.

**In scope:**
- UI element to show new care instructions count in the IPD care view
- Configuration concepts (same `formConcepts` config as story #104256 in `ipdDashboard/app.json`)
- Redirect and autoscroll to Care Instructions table on click of the hyperlink

**Out of scope:**
- Removal of new care instructions from the IPD care view

**Repositories:** openmrs-module-ipd, openmrs-module-ipd-frontend

### Acceptance Criteria

1. **AC1**: Given the doctor has filled out a form with instructions (on the Not Acknowledged tab), when the nurse is on the IPD care view dashboard, then the nurse should see `[Number] new care instructions` where number = rows in the Care Instructions Not Acknowledged tab (configured concepts in instructions display control).

2. **AC2**: Given there are new care instructions in the IPD care view, when the nurse clicks on the acknowledge hyperlink, then the nurse should be redirected to the patient's IPD dashboard AND autoscrolled to the Care Instructions table.

**AC Status**: Clear

### Comments & Context
- Story #104256 already created the CareInstructions display control in the IPD Dashboard with `formConcepts` config in `ipdDashboard/app.json` (componentKey `"CI"`).
- The Care View Dashboard already loads `ipdDashboard/app.json` via `getDashboardConfig()` into `ipdConfig` (available in CareViewContext) — `formConcepts` config is already accessible on the frontend without any config changes.
- Figma design shows the care instructions notification below the medications notification, using the same green `ResultNew20` icon and `.treatments-notification` pattern.
- Passing two lists (`visitUuids` + `conceptNames`) as query params creates URL length issues and serialization ambiguity — a POST body is used instead.

---

## Codebase Assessment

### Affected Files

**bahmnicore-omod (backend)**
| File | Change Type |
|------|-------------|
| `src/main/java/.../web/v1_0/controller/display/controls/BahmniObservationsController.java` | Modify — add POST /batch endpoint |
| `src/main/java/.../web/contract/BahmniObservationsBatchRequest.java` | Create — request DTO |
| `src/main/java/.../web/contract/VisitObservationsResponse.java` | Create — response DTO |

**openmrs-module-ipd-frontend**
| File | Change Type |
|------|-------------|
| `src/constants.js` | Modify — add `CARE_INSTRUCTIONS_BATCH_URL` |
| `src/features/DisplayControls/CareInstructions/utils/CareInstructionsUtils.jsx` | Modify — add `fetchCareInstructionsObsBatch()` |
| `src/features/CareViewPatientsSummary/components/CareViewPatientsSummary.jsx` | Modify — fetch batch count, pass to PatientDetailsCell |
| `src/features/CareViewPatientsSummary/components/PatientDetailsCell.jsx` | Modify — add care instructions notification block |
| `src/utils/CommonUtils.jsx` | Modify — add `scrollTo` param support in `getIPDPatientDashboardUrl` |
| `src/entries/Dashboard/Dashboard.jsx` | Modify — handle `scrollTo=CI` URL param for autoscroll |
| `src/features/CareViewPatientsSummary/components/PatientDetailsCell.spec.jsx` | Modify — add tests |
| `src/features/CareViewPatientsSummary/components/CareViewPatientsSummary.spec.jsx` | Modify — add tests |
| `src/entries/Dashboard/Dashboard.spec.jsx` | Modify — add autoscroll tests |

### Existing Patterns

**Notification pattern in PatientDetailsCell (lines 125–178):**
- `newTreatments > 0` → `.treatments-notification` div with green `ResultNew20` icon + count + `Link`
- `previousShiftPendingTasks.length > 0` → same div with red `WarningAlt20`
- Both navigate via `getIPDPatientDashboardUrl(patientUuid, visitUuid, "careViewDashboard")`
- Care instructions notification follows the exact same structure

**Data fetching in CareViewPatientsSummary:**
- `fetchSlots()`, `fetchTasks()`, `fetchPreviousShiftTasks()` all called in `useEffect` on `patientsSummary` change
- Results stored in state and passed as props to `PatientDetailsCell`
- `fetchCareInstructionsCount()` follows this same pattern

**Existing CareInstructions utilities (story #104256):**
- `fetchCareInstructionsObs(visitUuid, conceptNames)` — single-visit fetch (batch sibling added alongside)
- `mapObservationsToInstructions(observations, formConcepts)` — reused as-is to count rows per visit

**IPD Dashboard scroll mechanism:**
- `scrollToSection(key)` uses `refs.current[componentKey]` + `window.scrollTo`
- Already reads URL params: `openAcknowledge`, `medicationAdministrationUuid`
- Add `scrollTo` param → call `scrollToSection("CI")` after sections render

**Batch API pattern (IPDScheduleController):**
- `patientsMedicationSummary` accepts `List<String> patientUuids` → returns one object per patient
- Our batch obs endpoint mirrors this: `POST` body with `visitUuids` + `conceptNames` → one `{visitUuid, observations[]}` per visit

### CLAUDE.md Guidelines
- React micro-frontend, Carbon Components v10, React Context, React Intl, Axios (`withCredentials: true`)
- Jest + React Testing Library; test files co-located (`ComponentName.spec.jsx`)

### Test Patterns
- `axios-mock-adapter` for HTTP mocking
- Snapshot + behavioural tests co-located

---

## Implementation Approach

### Recommended Approach: POST Batch API in openmrs-module-ipd

Single HTTP call for the entire ward page. Frontend sends all patient visit UUIDs + configured concept names; backend returns observations grouped by visit; frontend counts using existing `mapObservationsToInstructions` utility.

---

#### Backend: New POST Batch Endpoint in bahmnicore-omod (BahmniObservationsController)

**Context:** Extends the existing `BahmniObservationsController` which already handles observations by `visitUuid` (GET endpoint at lines 98-115). The new POST batch endpoint reuses the same service methods, accepting a list of visitUuids and all optional filtering parameters.

**Existing GET Endpoint** (lines 98-115):
```java
@RequestMapping(method = RequestMethod.GET, params = {"visitUuid"})
public Collection<BahmniObservation> get(
    @RequestParam(value = "visitUuid", required = true) String visitUuid,
    @RequestParam(value = "scope", required = false) String scope,
    @RequestParam(value = "concept", required = false) List<String> conceptNames,
    @RequestParam(value = "obsIgnoreList", required = false) List<String> obsIgnoreList,
    @RequestParam(value = "filterObsWithOrders", required = false, defaultValue = "true") Boolean filterObsWithOrders
)
```

**API Contract — New POST Batch Endpoint:**

```
POST /rest/v1/bahmnicore/observations/batch
Content-Type: application/json
```

**Request** (with all parameters):
```json
{
  "visitUuids": [
    "visit-aaa",
    "visit-bbb",
    "visit-ccc"
  ],
  "concept": [
    "Physician Orders Comments",
    "Instruction for the Ward",
    "Post Operative Order Comments",
    "Cast and Dressing Order Comments",
    "Activity Until Further Notice",
    "Physical Therapy Comments",
    "Planned Return to Operating Room",
    "Planned Return Date to OR",
    "NPO Clear Fluid Until",
    "NPO Breast Milk Until",
    "NPO Cow's Milk / Food Until",
    "NPO Early Breakfast Until"
  ],
  "scope": "latest",
  "obsIgnoreList": [],
  "filterObsWithOrders": true
}
```

**Request** (minimal - only required params):
```json
{
  "visitUuids": ["visit-aaa", "visit-bbb", "visit-ccc"],
  "concept": ["Physician Orders Comments", "Instruction for the Ward"]
}
```

**Response:**
```json
[
  {
    "visitUuid": "visit-aaa",
    "observations": [
      {
        "concept": { "name": "Physician Orders Comments" },
        "value": "Keep NPO after midnight",
        "encounterDateTime": 1744000000000,
        "encounterUuid": "enc-111",
        "formFieldPath": "Patient Progress Notes and Orders.Physician Orders Comments/1-1",
        "providers": [{ "name": "Dr. Abebe" }]
      }
    ]
  },
  {
    "visitUuid": "visit-bbb",
    "observations": []
  },
  {
    "visitUuid": "visit-ccc",
    "observations": [
      {
        "concept": { "name": "Post Operative Order Comments" },
        "value": "Wound check daily",
        "encounterDateTime": 1744002000000,
        "encounterUuid": "enc-221",
        "formFieldPath": "Orthopaedic Operative Report.Post Operative Order Comments/1-1",
        "providers": [{ "name": "Dr. Tesfaye" }]
      }
    ]
  }
]
```

**Request DTO** `BahmniObservationsBatchRequest.java`:
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BahmniObservationsBatchRequest {
    private List<String> visitUuids;              // REQUIRED: list of visit UUIDs
    private List<String> concept;                 // OPTIONAL: concept names to filter
    private String scope;                         // OPTIONAL: "latest", "initial", or null for all
    private List<String> obsIgnoreList;           // OPTIONAL: concept names to ignore
    private Boolean filterObsWithOrders;          // OPTIONAL: default true on backend

    public BahmniObservationsBatchRequest() {}

    // getters + setters
    public List<String> getVisitUuids() { return visitUuids; }
    public void setVisitUuids(List<String> visitUuids) { this.visitUuids = visitUuids; }

    public List<String> getConcept() { return concept; }
    public void setConcept(List<String> concept) { this.concept = concept; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public List<String> getObsIgnoreList() { return obsIgnoreList; }
    public void setObsIgnoreList(List<String> obsIgnoreList) { this.obsIgnoreList = obsIgnoreList; }

    public Boolean getFilterObsWithOrders() { return filterObsWithOrders != null ? filterObsWithOrders : true; }
    public void setFilterObsWithOrders(Boolean filterObsWithOrders) { this.filterObsWithOrders = filterObsWithOrders; }
}
```

**Response DTO** `VisitObservationsResponse.java`:
```java
public class VisitObservationsResponse {
    private String visitUuid;
    private Collection<BahmniObservation> observations;

    public VisitObservationsResponse() {}

    public VisitObservationsResponse(String visitUuid, Collection<BahmniObservation> observations) {
        this.visitUuid = visitUuid;
        this.observations = observations;
    }

    public String getVisitUuid() { return visitUuid; }
    public void setVisitUuid(String visitUuid) { this.visitUuid = visitUuid; }

    public Collection<BahmniObservation> getObservations() { return observations; }
    public void setObservations(Collection<BahmniObservation> observations) { this.observations = observations; }
}
```

**Controller Method** (in `BahmniObservationsController.java`):
```java
@RequestMapping(value = "/batch", method = RequestMethod.POST)
@ResponseBody
public List<VisitObservationsResponse> getBatchObservations(
    @RequestBody BahmniObservationsBatchRequest request
) {
    Visit visit;
    List<Concept> concepts = MiscUtils.getConceptsForNames(request.getConcept(), conceptService);
    List<Concept> obsIgnoreConcepts = MiscUtils.getConceptsForNames(request.getObsIgnoreList(), conceptService);
    Boolean filterObsWithOrders = request.getFilterObsWithOrders();

    return request.getVisitUuids().stream()
        .map(visitUuid -> {
            visit = visitService.getVisitByUuid(visitUuid);
            Collection<BahmniObservation> observations;

            if (ObjectUtils.equals(request.getScope(), "INITIAL")) {
                observations = bahmniObsService.getInitialObsByVisit(
                    visit,
                    concepts,
                    request.getObsIgnoreList(),
                    filterObsWithOrders
                );
            } else if (ObjectUtils.equals(request.getScope(), "LATEST")) {
                observations = bahmniObsService.getLatestObsByVisit(
                    visit,
                    concepts,
                    request.getObsIgnoreList(),
                    filterObsWithOrders
                );
            } else {
                // Default: return all observations for the specified concepts
                observations = bahmniObsService.getObservationForVisit(
                    visitUuid,
                    request.getConcept(),
                    obsIgnoreConcepts,
                    filterObsWithOrders,
                    null
                );
            }

            return new VisitObservationsResponse(visitUuid, observations);
        })
        .collect(Collectors.toList());
}
```

**Key Design Decisions:**
- ✅ New POST endpoint `/batch` in existing `BahmniObservationsController`
- ✅ `@JsonInclude(NON_NULL)` ensures optional params omitted from response if not provided
- ✅ Backend defaults `filterObsWithOrders` to `true` if null
- ✅ Reuses all existing `BahmniObsService` methods (getInitialObsByVisit, getLatestObsByVisit, getObservationForVisit)
- ✅ Applies optional parameters consistently across all visitUuids
- ✅ No duplicate logic — mirrors GET endpoint behavior exactly
- ✅ Single HTTP call for entire ward page (10+ patients)

---

#### Frontend changes

**`constants.js`** — new URL constant:
```javascript
export const OBSERVATIONS_BATCH_URL =
  "/rest/" + RESTWS_V1 + "/bahmnicore/observations/batch";
```

**`CareInstructionsUtils.jsx`** — new batch fetch function (respects optional params):
```javascript
export const fetchBatchObservations = async (visitUuids, concepts, options = {}) => {
  try {
    // Build request body with only provided optional params
    const request = {
      visitUuids,
      concept: concepts
    };

    // Only add optional params if explicitly provided (not sent as null/undefined)
    if (options.scope) {
      request.scope = options.scope;
    }
    if (options.obsIgnoreList?.length > 0) {
      request.obsIgnoreList = options.obsIgnoreList;
    }
    if (options.filterObsWithOrders !== undefined) {
      request.filterObsWithOrders = options.filterObsWithOrders;
    }

    const response = await axios.post(
      OBSERVATIONS_BATCH_URL,
      request,
      { withCredentials: true }
    );
    return response.data; // [{ visitUuid, observations[] }, ...]
  } catch (error) {
    console.error("Failed to fetch observations batch", error);
    return [];
  }
};
```

**`CareViewPatientsSummary.jsx`** — fetch count map:
```javascript
const [careInstructionsCountMap, setCareInstructionsCountMap] = useState({});

const fetchCareInstructionsCount = async (patients) => {
  const ciSection = ipdConfig?.sections?.find(s => s.componentKey === "CI");
  const formConcepts = ciSection?.config?.formConcepts ?? [];
  if (formConcepts.length === 0) return;

  const concepts = [...new Set(formConcepts.flatMap(fc => fc.concepts))];
  const visitUuids = patients.map(p => p.visitDetails.uuid);

  // Call batch API with only required params; optional params are omitted
  const batchResult = await fetchBatchObservations(visitUuids, concepts);

  const countMap = {};
  batchResult.forEach(({ visitUuid, observations }) => {
    const instructions = mapObservationsToInstructions(observations, formConcepts);
    countMap[visitUuid] = instructions.length;
  });
  setCareInstructionsCountMap(countMap);
};

// Add to existing useEffect:
useEffect(() => {
  if (patientsSummary.length > 0) {
    fetchPreviousShiftTasks(patientsSummary);
    fetchSlots(patientsSummary);
    fetchTasks(patientsSummary);
    fetchCareInstructionsCount(patientsSummary); // new
  }
}, [patientsSummary, navHourEpoch]);
```

**`PatientDetailsCell.jsx`** — new notification block (after `newTreatments` block):
```jsx
{newCareInstructions > 0 && (
  <div className="treatments-notification" data-testid="new-care-instructions-notification">
    <div className="warning_icon">
      <ResultNew20 className="result-new-icon-20" />
    </div>
    <div className="treatments-notification-span">
      <div>
        &bull; {newCareInstructions + " New Care Instructions(s): "}
        <Link
          href={getIPDPatientDashboardUrl(
            patientDetails.uuid,
            visitDetails.uuid,
            "careViewDashboard",
            "CI"
          )}
          data-testid="care-instructions-ipd-dashboard"
        >
          <FormattedMessage id="ACKNOWLEDGE" defaultMessage="Acknowledge" />
        </Link>
      </div>
    </div>
  </div>
)}
```

**`CommonUtils.jsx`** — extend URL helper with optional `scrollTo`:
```javascript
export const getIPDPatientDashboardUrl = (
  patientUuid,
  visitUuid,
  source = "clinical",
  scrollTo = null
) => {
  const base = `/bahmni/clinical/#/default/patient/${patientUuid}/dashboard/visit/ipd/${visitUuid}?source=${source}`;
  return scrollTo ? `${base}&scrollTo=${scrollTo}` : base;
};
```

**`Dashboard.jsx`** — handle `scrollTo` URL param after sections load:
```javascript
const scrollTo = urlParams.get("scrollTo");

useEffect(() => {
  if (scrollTo && sections.length > 0 && refs.current[scrollTo]) {
    scrollToSection(scrollTo);
  }
}, [sections]);
```

---

**Pros:**
- ✅ 1 HTTP call for all patients on the page (vs N calls)
- ✅ POST body cleanly separates required + optional params — no URL length issues
- ✅ Backend filters obs by concept before returning — no excess data
- ✅ Reuses existing `mapObservationsToInstructions` utility for counting
- ✅ No config changes needed — `formConcepts` already in `ipdConfig`
- ✅ Extends existing `BahmniObservationsController` — no new controller
- ✅ Optional parameters only sent if explicitly provided (cleaner request payloads)
- ✅ Backend `@JsonInclude(NON_NULL)` prevents null values in response
- ✅ Backend defaults `filterObsWithOrders=true` if omitted
- ✅ Mirrors existing GET endpoint behavior exactly (same BahmniObsService methods)

**Optional Parameter Handling:**
- **Frontend:** Only includes `scope`, `obsIgnoreList`, `filterObsWithOrders` in request if explicitly provided via `options` parameter
- **Backend:** `@JsonInclude(NON_NULL)` annotation ensures null values are not serialized in JSON response
- **Minimal Request:** Care instructions use case sends only `{ visitUuids, concept }` — optional params omitted by default
- **Flexible Usage:** Future use cases can pass `{ scope: "latest", filterObsWithOrders: false }` to override defaults

**Complexity/Risk**: Low-Medium

---

## Work Breakdown

| Sub-task | Effort | Files | Tests |
|----------|--------|-------|-------|
| 1. Backend: POST /batch endpoint + DTOs in BahmniObservationsController | ~0.4 pts | `BahmniObservationsController.java` (modify), `BahmniObservationsBatchRequest.java` (new), `VisitObservationsResponse.java` (new) | JUnit |
| 2. Frontend: batch utility + URL constant | ~0.2 pts | `CareInstructionsUtils.jsx`, `constants.js` | Unit tests |
| 3. Frontend: fetch count map in CareViewPatientsSummary | ~0.3 pts | `CareViewPatientsSummary.jsx` | Unit tests |
| 4. Frontend: render notification in PatientDetailsCell | ~0.3 pts | `PatientDetailsCell.jsx` | Unit tests |
| 5. Frontend: URL helper + autoscroll in Dashboard | ~0.3 pts | `CommonUtils.jsx`, `Dashboard.jsx` | Unit tests |
| 6. Frontend: spec updates across all changed files | ~0.4 pts | `*.spec.jsx` | — |

**Total estimated**: 2.0 points

---

## Scope Verdict

- [x] ✅ Confirmed 2-pointer — moderate complexity, focused scope

Work involves one thin backend controller (delegates to existing `BahmniObsService`), one new utility function, one notification UI block following an exact existing pattern, and URL-based autoscroll. No new React components, no config changes, no schema changes.

---

## Clarifying Questions

No clarifying questions — requirements and design are clear.
