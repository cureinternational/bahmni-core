# Refactoring Summary: 105551 - Shared Utility Implementation

**Hive Action**: 105551
**Story Points**: 2
**Status**: Complete
**Refactoring Type**: Code consolidation and deduplication

---

## Overview

Refactored the implementation to eliminate code duplication by creating a single shared utility function `loadCareInstructionsForPatient` that both the CareInstructions component (for display) and CareView (for counting notifications) can use. Instead of passing a count to CareView, the full instruction list is now passed, allowing CareView to count it during render.

---

## What Changed

### 1. Core Utility Refactoring

**File**: `src/features/DisplayControls/CareInstructions/utils/CareInstructionsUtils.jsx`

**Before**:
- Had two separate functions with duplicated logic:
  - `fetchCareInstructionsCountForPatient` - returned only a count
  - `extractInstructionsFromObs` - extracted obs values

**After**:
- Created unified `loadCareInstructionsForPatient(patientUuid, visitUuid, formConcepts, allFormsFilledInCurrentVisit)` function that:
  - Takes the full forms data as an optional parameter
  - If not provided, fetches it using patientUuid and visitUuid
  - Returns the full instruction array with all fields: `{id, encounterDateTime, form, instructionType, instruction, providerName, action}`
  - Sorts by encounterDateTime (newest first)
  - Keeps `extractInstructionsFromObs` and `fetchEncounterObs` as helper utilities

**Benefits**:
- Single source of truth for instruction extraction logic
- Reusable across components
- More flexible - can work with pre-fetched forms or fetch on demand

### 2. CareInstructions Component Refactoring

**File**: `src/features/DisplayControls/CareInstructions/components/CareInstructions.jsx`

**Before**:
- Had 50+ lines of inline instruction loading logic in useEffect
- Duplicated form filtering, obs fetching, and instruction extraction

**After**:
- Simplified to call `loadCareInstructionsForPatient` with local form data
- Cleaner, more maintainable code
- Imports reduced: removed `fetchEncounterObs` and `extractInstructionsFromObs` direct imports

### 3. CareView Notification Integration

**File**: `src/features/CareViewPatientsSummary/components/CareViewPatientsSummary.jsx`

**Before**:
- Fetched care instruction **counts** per patient
- State: `careInstructionsCounts` (object mapping patientUuid → count)
- Passed count prop to PatientDetailsCell

**After**:
- Fetches full care instruction **lists** per patient
- State: `careInstructions` (object mapping patientUuid → array)
- Passes full array to PatientDetailsCell
- Function renamed from `fetchCareInstructionsCounts` to `fetchCareInstructions`

### 4. PatientDetailsCell Notification Display

**File**: `src/features/CareViewPatientsSummary/components/PatientDetailsCell.jsx`

**Before**:
- Prop: `careInstructionsCount: number`
- Condition: `careInstructionsCount > 0`
- Display: `{careInstructionsCount + " "}`

**After**:
- Prop: `careInstructions: array`
- Condition: `careInstructions && careInstructions.length > 0`
- Display: `{careInstructions.length + " "}`
- PropTypes updated to reflect array type

### 5. Test Updates

**Files**:
- `PatientDetailsCell.spec.jsx` - Updated 3 care instructions tests to pass array instead of count
- `CareViewPatientsSummary.spec.jsx` - Updated mock to use new `loadCareInstructionsForPatient` function
- `CareInstructions.spec.jsx` - Updated mock implementation to call the new shared function

**Key Changes**:
- All tests pass care instruction arrays as fixtures instead of counts
- Mock function reuses `extractInstructionsFromObs` to generate realistic test data
- Tests validate both list and count-based rendering correctly

---

## Code Quality Improvements

1. **DRY Principle**: Eliminated ~50 lines of duplicated instruction loading logic
2. **Consistency**: Both CareInstructions and CareView use the same function for instruction extraction
3. **Maintainability**: Single point of change for instruction loading logic
4. **Flexibility**: Function works with both pre-loaded form data and on-demand fetching

---

## Test Results

✅ All tests passing (459 passed, 7 skipped)
✅ All snapshots valid (51 passed)
✅ Full coverage maintained for refactored code

---

## Acceptance Criteria Status

| Criterion | Status | Notes |
|-----------|--------|-------|
| AC1: Nurse sees "[Number] new care instructions" in CareView | ✅ | Count computed from same instruction list extraction |
| AC2: Clicking acknowledge link redirects and autoscrolls | ✅ | Auto-scroll functionality unchanged |

---

## Breaking Changes

None. This is a **non-breaking refactoring**. The external contract remains the same:
- PatientDetailsCell still displays the notification when care instructions exist
- Dashboard still auto-scrolls to Care Instructions section
- CareInstructions component still displays the full table of instructions

---

## Files Modified

| File | Type | Lines Changed |
|------|------|------|
| `CareInstructionsUtils.jsx` | Modified | Utility refactored |
| `CareInstructions.jsx` | Modified | Simplified to use shared utility |
| `CareViewPatientsSummary.jsx` | Modified | Updated to fetch full lists |
| `PatientDetailsCell.jsx` | Modified | Updated to accept/count array |
| `PatientDetailsCell.spec.jsx` | Modified | Test fixtures updated |
| `CareViewPatientsSummary.spec.jsx` | Modified | Mock updated for new function |
| `CareInstructions.spec.jsx` | Modified | Mock implementation refactored |

**Total files modified**: 7
**Total lines of code eliminated**: ~50 (duplicated logic)

---

## Implementation Pattern

The refactoring follows a clean pattern where the utility is designed to be flexible:

```javascript
// CareInstructions: uses with form data it already has
const instructions = await loadCareInstructionsForPatient(
  null, null, formConcepts, allFormsFilledInCurrentVisit
);

// CareView: fetches form data on demand if not provided
const instructions = await loadCareInstructionsForPatient(
  patientUuid, visitUuid, formConcepts
);
// Function internally calls fetchFormData() if allFormsFilledInCurrentVisit is not provided
```

This design allows both components to use the same function while fitting their different operational patterns.

---

## Verification Steps

1. ✅ All 459 unit tests pass
2. ✅ All 51 snapshots match
3. ✅ No lint errors
4. ✅ Code follows project conventions (React patterns, context usage)
5. ✅ Shared utility is properly tested with unit tests for `extractInstructionsFromObs`
6. ✅ Integration between CareView and CareInstructions validated

---

## Notes for Future Maintainers

- The `loadCareInstructionsForPatient` function is the canonical source for instruction extraction logic
- If instruction structure changes, update only this function
- The function gracefully handles missing form data by fetching on demand
- Consider future optimization: a batch endpoint for fetching multiple patients' instructions at once (noted as follow-up item in original implementation)
