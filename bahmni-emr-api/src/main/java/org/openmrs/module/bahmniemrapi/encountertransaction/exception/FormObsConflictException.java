package org.openmrs.module.bahmniemrapi.encountertransaction.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when an encounter save carries new form observations (no matching uuid on the encounter) for a
 * formNamespace/formFieldPath/concept that the encounter already holds. This happens when the client works from
 * stale form state, e.g. the same form was saved from another browser tab or the same payload was submitted twice.
 * Saving it would silently create a second copy of the observation.
 */
public class FormObsConflictException extends RuntimeException {

    public static final String CODE = "FORM_OBS_CONFLICT";

    private final List<String> conflictingFieldPaths;

    /**
     * Fixed text on purpose: the UI maps this exact message to a localized one, and the Bahmni http error
     * interceptor compares the part after the first colon. Keep it free of colons and counts; the conflicting
     * paths are carried separately.
     */
    public static final String MESSAGE = "Form data is out of date. Please reload the form and re-apply your changes.";

    public FormObsConflictException(List<String> conflictingFieldPaths) {
        super(MESSAGE);
        this.conflictingFieldPaths = Collections.unmodifiableList(new ArrayList<>(conflictingFieldPaths));
    }

    public List<String> getConflictingFieldPaths() {
        return conflictingFieldPaths;
    }
}
