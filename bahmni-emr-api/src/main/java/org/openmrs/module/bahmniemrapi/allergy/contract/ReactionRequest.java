package org.openmrs.module.bahmniemrapi.allergy.contract;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReactionRequest {
    private String reaction;
    private String nonCodedReaction;

    public String getReaction() {
        return reaction;
    }

    public void setReaction(String reaction) {
        this.reaction = reaction;
    }

    public String getNonCodedReaction() {
        return nonCodedReaction;
    }

    public void setNonCodedReaction(String nonCodedReaction) {
        this.nonCodedReaction = nonCodedReaction;
    }
}
