# Implementation Summary: 105551
# Displaying new care instructions in the IPD care view

**Hive Action**: 105551
**Story Points**: 2
**Status**: Complete

---

## Files Modified

### openmrs-module-ipd-frontend

| File | Change Type | Description |
|------|-------------|-------------|
| `src/features/DisplayControls/CareInstructions/utils/CareInstructionsUtils.jsx` | Modified | Added `fetchCareInstructionsCountForPatient` utility |
| `src/features/CareViewPatientsSummary/components/CareViewPatientsSummary.jsx` | Modified | Added care instructions count fetching per patient batch |
| `src/features/CareViewPatientsSummary/components/PatientDetailsCell.jsx` | Modified | Added care instructions notification block |
| `src/entries/Dashboard/Dashboard.jsx` | Modified | Added `scrollToSection` URL param + auto-scroll on mount |
| `src/features/CareViewPatientsSummary/tests/PatientDetailsCell.spec.jsx` | Modified | Added 3 tests for care instructions notification |
| `src/features/CareViewPatientsSummary/tests/CareViewPatientsSummary.spec.jsx` | Modified | Added 1 test for care instructions count fetching |

**Total files modified**: 6

### cure-bahmni-emr

No changes needed — `ipdConfig` (containing formConcepts for Care Instructions) is already loaded in CareView context.

---

## Implementation Summary

### What Was Done

1. **Count utility** — `fetchCareInstructionsCountForPatient(patientUuid, visitUuid, formConcepts)` added to `CareInstructionsUtils.jsx`. Fetches form data for a patient's visit, filters matching forms, fetches encounter obs per form, extracts configured concepts, returns total count.

2. **Data fetching** — `CareViewPatientsSummary` now extracts `formConcepts` from `ipdConfig.sections` (componentKey "CI") and calls the count utility per patient in parallel via `Promise.all`, alongside existing slot/task fetching.

3. **Notification UI** — `PatientDetailsCell` shows a new notification when `careInstructionsCount > 0`: green `ResultNew20` icon + "N new care instruction(s): Acknowledge" where Acknowledge is a link to the IPD Dashboard with `&scrollToSection=CI`.

4. **Auto-scroll** — `Dashboard.jsx` reads `scrollToSection` URL param and auto-scrolls to the specified section (e.g., "CI") after sections render, with a 500ms delay for DOM readiness.

### Approach Followed

Approach A from discovery plan — Frontend per-patient data fetching. Reuses existing `fetchFormData`, `fetchEncounterObs`, and `extractInstructionsFromObs` utilities.

### Deviations from Plan

None — followed plan exactly.

---

## Refactoring Performed

No refactoring performed. All changes are additive.

---

## Work Breakdown Completion

| Sub-task | Status | Notes |
|----------|--------|-------|
| 1. Add `fetchCareInstructionsCountForPatient` utility | ✅ | Reuses existing helpers |
| 2. Fetch care instructions counts in CareViewPatientsSummary | ✅ | Parallel fetch via Promise.all |
| 3. Add notification in PatientDetailsCell | ✅ | Follows exact medication notification pattern |
| 4. Add `scrollToSection` URL param + auto-scroll in Dashboard | ✅ | 500ms delay for DOM readiness |
| 5. Write/update tests | ✅ | 4 new tests added |

**Completion**: 5/5 sub-tasks complete

---

## Test Results

**Status**: All passing

### Tests Added/Modified
- `PatientDetailsCell.spec.jsx`: 3 new tests — renders notification when count > 0, does not render when count is 0, link includes scrollToSection=CI
- `CareViewPatientsSummary.spec.jsx`: 1 new test — verifies fetchCareInstructionsCountForPatient is called for each patient

### Test Output
```
Test Suites: 1 skipped, 71 passed, 71 of 72 total
Tests:       7 skipped, 459 passed, 466 total
Snapshots:   51 passed, 51 total
Time:        22.72s
```

---

## Acceptance Criteria Checklist

| Criterion | Status | Notes |
|-----------|--------|-------|
| AC1: Nurse sees "[Number] new care instructions" in CareView where number = rows in Not Acknowledged tab | ✅ | Count computed from same formConcepts config, same extraction logic as CareInstructions component |
| AC2: Clicking acknowledge link redirects to IPD dashboard and autoscrolls to Care Instructions table | ✅ | Link appends `&scrollToSection=CI`, Dashboard.jsx reads param and scrolls to CI section |

**AC Status**: 2/2 met

---

## Follow-up Items

- **Performance optimization**: For wards with many patients (50+), the per-patient form data + encounter obs fetching could be slow. A future backend API for care instruction counts per patient batch would improve this.
- **Task filter integration**: The "NEW" task filter in CareView currently only filters by `newTreatments`. A future story could integrate care instructions count into this filter.

---

## PR Description (Ready to Copy)

**Hive Action**: 105551

### What Changed

**openmrs-module-ipd-frontend:**
- New `fetchCareInstructionsCountForPatient` utility to count unacknowledged care instructions per patient
- CareView now fetches care instructions counts alongside medication/task data
- PatientDetailsCell shows "N new care instruction(s): Acknowledge" notification (same pattern as medications)
- IPD Dashboard auto-scrolls to a section when `scrollToSection` URL param is present

### Why
IPD nurses need to see new care instructions directly in the CareView ward dashboard and navigate to the Care Instructions table with one click.

### How Tested
- [x] Unit tests added (4 new tests)
- [x] All tests passing (459 passed)
- [x] Lint passing

### Acceptance Criteria Met
- [x] Nurse sees "[Number] new care instructions" in CareView (count matches Not Acknowledged tab rows)
- [x] Clicking acknowledge link redirects to IPD dashboard and autoscrolls to Care Instructions table
