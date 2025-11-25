package org.openmrs.module.bahmniemrapi.allergy.contract;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BahmniAllergyRequest {
    private AllergenRequest allergen;
    private List<ReactionRequest> reactions;
    private String severity;
    private String comment;

    public AllergenRequest getAllergen() {
        return allergen;
    }

    public void setAllergen(AllergenRequest allergen) {
        this.allergen = allergen;
    }

    public List<ReactionRequest> getReactions() {
        return reactions;
    }

    public void setReactions(List<ReactionRequest> reactions) {
        this.reactions = reactions;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
