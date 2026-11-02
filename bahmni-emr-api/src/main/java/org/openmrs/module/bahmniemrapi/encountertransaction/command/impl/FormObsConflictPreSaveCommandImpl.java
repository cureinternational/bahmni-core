package org.openmrs.module.bahmniemrapi.encountertransaction.command.impl;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.EncounterService;
import org.openmrs.module.bahmniemrapi.encountertransaction.command.EncounterDataPreSaveCommand;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniEncounterTransaction;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.bahmniemrapi.encountertransaction.exception.FormObsConflictException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Obs on an existing encounter are matched by uuid only. A form2 observation that arrives without a uuid the encounter
 * knows about is therefore always inserted, even when the encounter already holds that field. This happens when a
 * client saves from stale form state (another tab saved first) or resubmits a payload.
 *
 * Rejects such a save instead of creating a second copy of the same formNamespace/formFieldPath/concept.
 * Only applies to a save that targets an existing encounter and to observations that carry a form field path.
 */
@Component
public class FormObsConflictPreSaveCommandImpl implements EncounterDataPreSaveCommand {

    private final EncounterService encounterService;

    @Autowired
    public FormObsConflictPreSaveCommandImpl(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    @Override
    public BahmniEncounterTransaction update(BahmniEncounterTransaction bahmniEncounterTransaction) {
        String encounterUuid = bahmniEncounterTransaction.getEncounterUuid();
        if (StringUtils.isBlank(encounterUuid) || bahmniEncounterTransaction.getObservations() == null) {
            return bahmniEncounterTransaction;
        }
        Encounter encounter = encounterService.getEncounterByUuid(encounterUuid);
        if (encounter == null) {
            return bahmniEncounterTransaction;
        }

        // A field the same payload voids is free again, e.g. a section removed and re-added in one save.
        Set<String> voidedInPayload = new HashSet<>();
        collectVoidedUuids(bahmniEncounterTransaction.getObservations(), voidedInPayload);

        Set<String> existingUuids = new HashSet<>();
        Set<String> occupiedFields = new HashSet<>();
        collectExisting(encounter.getObsAtTopLevel(false), false, voidedInPayload, existingUuids, occupiedFields);
        if (occupiedFields.isEmpty()) {
            return bahmniEncounterTransaction;
        }

        Set<String> conflicts = new LinkedHashSet<>();
        findConflicts(bahmniEncounterTransaction.getObservations(), existingUuids, occupiedFields, conflicts);
        if (!conflicts.isEmpty()) {
            throw new FormObsConflictException(new ArrayList<>(conflicts));
        }
        return bahmniEncounterTransaction;
    }

    private void collectVoidedUuids(Collection<BahmniObservation> observations, Set<String> voidedUuids) {
        if (observations == null) {
            return;
        }
        for (BahmniObservation observation : observations) {
            if (observation.getVoided() && StringUtils.isNotBlank(observation.getUuid())) {
                voidedUuids.add(observation.getUuid());
            }
            collectVoidedUuids(observation.getGroupMembers(), voidedUuids);
        }
    }

    private void collectExisting(Collection<Obs> observations, boolean parentBeingVoided, Set<String> voidedInPayload,
                                 Set<String> uuids, Set<String> occupiedFields) {
        if (observations == null) {
            return;
        }
        for (Obs obs : observations) {
            if (obs.getVoided()) {
                continue;
            }
            uuids.add(obs.getUuid());
            boolean beingVoided = parentBeingVoided || voidedInPayload.contains(obs.getUuid());
            if (!beingVoided && StringUtils.isNotBlank(obs.getFormFieldPath()) && obs.getConcept() != null) {
                occupiedFields.add(fieldId(obs.getFormFieldNamespace(), obs.getFormFieldPath(), obs.getConcept().getUuid()));
            }
            collectExisting(obs.getGroupMembers(false), beingVoided, voidedInPayload, uuids, occupiedFields);
        }
    }

    private void findConflicts(Collection<BahmniObservation> observations, Set<String> existingUuids,
                               Set<String> occupiedFields, Set<String> conflicts) {
        if (observations == null) {
            return;
        }
        for (BahmniObservation observation : observations) {
            if (observation.getVoided()) {
                continue;
            }
            boolean knownToEncounter = StringUtils.isNotBlank(observation.getUuid()) && existingUuids.contains(observation.getUuid());
            if (!knownToEncounter && StringUtils.isNotBlank(observation.getFormFieldPath())
                    && occupiedFields.contains(fieldId(observation.getFormNamespace(), observation.getFormFieldPath(), observation.getConceptUuid()))) {
                conflicts.add(observation.getFormFieldPath());
            }
            findConflicts(observation.getGroupMembers(), existingUuids, occupiedFields, conflicts);
        }
    }

    private String fieldId(String namespace, String fieldPath, String conceptUuid) {
        return StringUtils.defaultString(namespace) + "|" + fieldPath + "|" + StringUtils.defaultString(conceptUuid);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
}
