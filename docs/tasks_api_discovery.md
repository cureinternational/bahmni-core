# Discovery: Non-Medication Task API & Care Instructions Linkage

**Topic**: Linking non-medication tasks to care instruction observations via `orderId`
**Created**: 2026-04-07
**Repositories**: openmrs-module-ipd-frontend, openmrs-module-fhir2Extension

---

## Context

When a nurse adds a non-medication task on the IPD dashboard, the frontend calls:

```
POST /openmrs/ws/rest/v1/tasks
```

The goal is to associate each non-medication task with the specific care instruction (observation) it was created for, so that tasks can be tracked against their originating obs. Each obs (care instruction) has an `orderId`.

---

## API Location

### Frontend

| File | Description |
|------|-------------|
| `src/constants.js:102` | `NON_MEDICATION_BASE_URL = RESTWS_V1 + "/tasks"` |
| `src/features/DisplayControls/NursingTasks/utils/EmergencyTasksUtils.js:61` | `saveNonMedicationTask()` — POST |
| `src/features/DisplayControls/NursingTasks/utils/NursingTasksUtils.js:240` | `updateNonMedicationTask()` — PUT |
| `src/features/DisplayControls/NursingTasks/components/AddEmergencyTasks.jsx:293` | `createNonMedicationTaskPayload()` — builds POST body |

### Backend

**Repository**: [Bahmni/openmrs-module-fhir2Extension](https://github.com/Bahmni/openmrs-module-fhir2Extension)

This is **not** a pure FHIR endpoint — it is a custom REST extension built on top of `openmrs-module-fhir2`. The base URL (`/openmrs/ws/rest/v1/tasks`) is served by a custom `TaskController` in the fhir2Extension module, not by the OpenMRS core REST API.

| File | Description |
|------|-------------|
| `omod/src/main/java/.../web/TaskController.java` | REST controller — POST, GET, PUT |
| `omod/src/main/java/.../web/contract/TaskRequest.java` | POST request DTO |
| `omod/src/main/java/.../web/contract/TaskUpdateRequest.java` | PUT request DTO |
| `omod/src/main/java/.../web/contract/TaskResponse.java` | Response DTO |
| `api/src/main/java/.../model/Task.java` | Internal model wrapping `FhirTask` |
| `api/src/main/java/.../mapper/TaskMapper.java` | Maps `TaskRequest` → `FhirTask` |

---

## Current API Capability

### POST `/openmrs/ws/rest/v1/tasks`

**Current request payload (frontend → backend):**

```json
{
  "name": "helloooo",
  "requestedStartTime": 1775563013000,
  "requestedEndTime": 1775563013000,
  "patientUuid": "cefd6f22-bca3-4536-97c4-dd6918acfa06",
  "encounterUuid": "5680e681-02e5-47c8-9a5e-e8f7f5f490ca",
  "intent": "ORDER",
  "taskType": null,
  "status": "REQUESTED"
}
```

**Full `TaskRequest` DTO fields (all currently supported):**

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | Task name / description |
| `patientUuid` | String | Patient identifier |
| `visitUuid` | String | Links task to visit via `forReference` |
| `encounterUuid` | String | Links task to encounter via `encounterReference` |
| `taskType` | String | Concept name (e.g. `"Wound Care"`) |
| `requestedStartTime` | Long (epoch ms) | Scheduled start |
| `requestedEndTime` | Long (epoch ms) | Scheduled end |
| `status` | String | `REQUESTED`, `ACCEPTED`, `COMPLETED`, `REJECTED` |
| `intent` | String | Always `"ORDER"` |
| `comment` | String | Optional free text |
| `isSystemGeneratedTask` | Boolean | For system-created tasks |

### GET `/openmrs/ws/rest/v1/tasks`

Query params: `startTime`, `endTime` (required) + one of `visitUuid` or `patientUuids`.

### PUT `/openmrs/ws/rest/v1/tasks`

Body: array of `TaskUpdateRequest` — each has `uuid` (required), `executionStartTime`, `executionEndTime`, `status`, `comment`.

---

## Underlying Data Model (`FhirTask`)

The `FhirTask` entity (from `openmrs-module-fhir2`, table `fhir_task`) has:

| Field | DB Column | Notes |
|-------|-----------|-------|
| `status` | `status` | Enum |
| `intent` | `intent` | Enum — only `ORDER` supported |
| `taskCode` | `task_code` | FK → Concept (task type) |
| `forReference` | `for_reference_id` | FK → `FhirReference` — points to Visit |
| `encounterReference` | `encounter_reference_id` | FK → `FhirReference` — optional |
| `basedOnReferences` | join table `fhir_task_based_on_reference` | `Set<FhirReference>` — **FHIR standard field for linking to orders** |
| `requestedStartTime` | `fhir_task_requested_period` table | One-to-one |
| `requestedEndTime` | `fhir_task_requested_period` table | One-to-one |

---

## Gap Analysis: Order / Observation Linkage

| Layer | Status |
|-------|--------|
| `FhirTask` entity | `basedOnReferences` field exists (`fhir_task_based_on_reference` join table) — standard FHIR mechanism for linking a Task to a ServiceRequest/MedicationRequest/Order |
| `TaskRequest` DTO | **No `orderId` or `basedOn` field** — not exposed |
| `TaskMapper` | **Does not populate `basedOnReferences`** |
| Frontend payload | **No `orderId` sent** |

**Conclusion**: The underlying FHIR data model already supports order linkage via `basedOnReferences`, but it is not wired up in the Bahmni extension layer (DTO → mapper → entity). No `orderId` field exists anywhere in the current request/response flow.

---

## Required Changes

### Backend (`openmrs-module-fhir2Extension`)

1. **`TaskRequest.java`** — add `orderId` field (the order UUID from the obs):
   ```java
   private String orderId;
   ```

2. **`TaskMapper.java`** — map `orderId` → `FhirTask.basedOnReferences`:
   ```java
   if (taskRequest.getOrderId() != null) {
       FhirReference orderRef = new FhirReference();
       orderRef.setReference(taskRequest.getOrderId());
       orderRef.setType("ServiceRequest");
       fhirTask.setBasedOnReferences(Collections.singleton(orderRef));
   }
   ```

3. **`TaskResponse.java`** — optionally expose `orderId` in response for traceability.

### Frontend (`openmrs-module-ipd-frontend`)

1. **`AddEmergencyTasks.jsx:293`** — extend `createNonMedicationTaskPayload()` to accept and include `orderId`:
   ```js
   const nonMedicationPayload = {
     name: task,
     requestedStartTime: utcTimeEpoch * 1000,
     requestedEndTime: utcTimeEpoch * 1000,
     patientUuid: patientId,
     encounterUuid: encounterUuid.encounterUuid,
     intent: "ORDER",
     taskType: nonMedicationTaskType ? nonMedicationTaskType : null,
     status: "REQUESTED",
     orderId: selectedCareInstruction?.orderId ?? null,   // NEW
   };
   ```

2. **Care Instructions UI** — pass the selected obs `orderId` through to `AddEmergencyTasks` when a task is created from a care instruction row.

---

## Open Questions

1. Is the backend change (`openmrs-module-fhir2Extension`) in scope for this story, or handled separately?
2. What is the shape of `orderId` on the obs object returned by the care instructions API — is it a UUID or numeric ID?
3. Should `orderId` be nullable (tasks not linked to any obs remain valid)?
4. Does `TaskResponse` need to return `orderId` so the frontend can display which task belongs to which instruction?
