package org.openmrs.module.bahmniemrapi.encountertransaction.command.impl;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniEncounterTransaction;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.bahmniemrapi.encountertransaction.exception.FormObsConflictException;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FormObsConflictPreSaveCommandImplTest {

    private static final String NAMESPACE = "Bahmni";
    private static final String ENCOUNTER_UUID = "encounter-uuid";

    private EncounterService encounterService;
    private FormObsConflictPreSaveCommandImpl command;
    private Encounter encounter;

    @Before
    public void setUp() {
        encounterService = mock(EncounterService.class);
        command = new FormObsConflictPreSaveCommandImpl(encounterService);
        encounter = new Encounter();
        encounter.setPatient(new Patient());
        when(encounterService.getEncounterByUuid(ENCOUNTER_UUID)).thenReturn(encounter);
    }

    @Test
    public void shouldAllowSaveWhenThereIsNoEncounterYet() {
        BahmniEncounterTransaction transaction = transaction(null, newObs("section.1/1-0", "c1"));

        assertSame(transaction, command.update(transaction));
    }

    @Test
    public void shouldAllowSaveWhenEncounterIsNotFound() {
        when(encounterService.getEncounterByUuid("unknown")).thenReturn(null);
        BahmniEncounterTransaction transaction = transaction("unknown", newObs("section.1/1-0", "c1"));

        assertSame(transaction, command.update(transaction));
    }

    @Test
    public void shouldRejectNewObsForAFieldTheEncounterAlreadyHas() {
        existingObs("existing-1", "Form.1/1-0", "c1");

        try {
            command.update(transaction(ENCOUNTER_UUID, newObs("Form.1/1-0", "c1")));
            fail("expected a conflict");
        } catch (FormObsConflictException e) {
            assertEquals(Collections.singletonList("Form.1/1-0"), e.getConflictingFieldPaths());
        }
    }

    @Test
    public void shouldRejectObsCarryingTheUuidOfAnObsThatWasAlreadyReplaced() {
        Obs replaced = existingObs("old-uuid", "Form.1/1-0", "c1");
        replaced.setVoided(true);
        existingObs("new-uuid", "Form.1/1-0", "c1");

        try {
            command.update(transaction(ENCOUNTER_UUID, obsWithUuid("old-uuid", "Form.1/1-0", "c1")));
            fail("expected a conflict");
        } catch (FormObsConflictException e) {
            assertEquals(Collections.singletonList("Form.1/1-0"), e.getConflictingFieldPaths());
        }
    }

    @Test
    public void shouldAllowEditOfAnObsTheEncounterKnows() {
        existingObs("existing-1", "Form.1/1-0", "c1");
        BahmniEncounterTransaction transaction = transaction(ENCOUNTER_UUID, obsWithUuid("existing-1", "Form.1/1-0", "c1"));

        assertSame(transaction, command.update(transaction));
    }

    @Test
    public void shouldAllowNewObsForADifferentField() {
        existingObs("existing-1", "Form.1/1-0", "c1");
        BahmniEncounterTransaction transaction = transaction(ENCOUNTER_UUID, newObs("Form.1/1-1", "c1"));

        assertSame(transaction, command.update(transaction));
    }

    @Test
    public void shouldAllowSameFieldPathForADifferentConcept() {
        existingObs("existing-1", "Form.1/1-0", "c1");
        BahmniEncounterTransaction transaction = transaction(ENCOUNTER_UUID, newObs("Form.1/1-0", "c2"));

        assertSame(transaction, command.update(transaction));
    }

    @Test
    public void shouldRejectNewGroupMemberForAFieldTheEncounterAlreadyHas() {
        Obs group = existingObs("group-1", "Form.1/2-0", "group");
        Obs child = obs("child-1", "Form.1/2/3-0", "c1");
        group.addGroupMember(child);

        BahmniObservation incomingGroup = obsWithUuid("group-1", "Form.1/2-0", "group");
        incomingGroup.setGroupMembers(Collections.singletonList(newObs("Form.1/2/3-0", "c1")));

        try {
            command.update(transaction(ENCOUNTER_UUID, incomingGroup));
            fail("expected a conflict");
        } catch (FormObsConflictException e) {
            assertEquals(Collections.singletonList("Form.1/2/3-0"), e.getConflictingFieldPaths());
        }
    }

    @Test
    public void shouldAllowAFieldToBeReusedWhenTheSamePayloadVoidsTheExistingObs() {
        existingObs("existing-1", "Form.1/1-2", "c1");
        BahmniObservation removed = obsWithUuid("existing-1", "Form.1/1-2", "c1");
        removed.setVoided(true);
        BahmniEncounterTransaction transaction = transaction(ENCOUNTER_UUID, removed, newObs("Form.1/1-2", "c1"));

        assertSame(transaction, command.update(transaction));
    }

    @Test
    public void shouldAllowGroupMembersToBeReusedWhenTheirGroupIsVoidedInTheSamePayload() {
        Obs group = existingObs("group-1", "Form.1/2-1", "group");
        group.addGroupMember(obs("child-1", "Form.1/2/3-1", "c1"));

        BahmniObservation removedGroup = obsWithUuid("group-1", "Form.1/2-1", "group");
        removedGroup.setVoided(true);
        BahmniObservation newGroup = newObs("Form.1/2-1", "group");
        newGroup.setGroupMembers(Collections.singletonList(newObs("Form.1/2/3-1", "c1")));

        BahmniEncounterTransaction transaction = transaction(ENCOUNTER_UUID, removedGroup, newGroup);

        assertSame(transaction, command.update(transaction));
    }

    @Test
    public void shouldIgnoreObsWithoutAFormFieldPath() {
        existingObs("existing-1", "Form.1/1-0", "c1");
        BahmniObservation plainObs = newObs(null, "c1");
        BahmniEncounterTransaction transaction = transaction(ENCOUNTER_UUID, plainObs);

        assertNotNull(command.update(transaction));
    }

    @Test
    public void shouldListEveryConflictingPath() {
        existingObs("existing-1", "Form.1/1-0", "c1");
        existingObs("existing-2", "Form.1/2-0", "c2");

        try {
            command.update(transaction(ENCOUNTER_UUID, newObs("Form.1/1-0", "c1"), newObs("Form.1/2-0", "c2")));
            fail("expected a conflict");
        } catch (FormObsConflictException e) {
            assertEquals(new HashSet<>(Arrays.asList("Form.1/1-0", "Form.1/2-0")), new HashSet<>(e.getConflictingFieldPaths()));
        }
    }

    @Test
    public void conflictMessageIsFixedTextWithoutColonsSoTheUiCanMapIt() {
        existingObs("existing-1", "Form.1/1-0", "c1");

        try {
            command.update(transaction(ENCOUNTER_UUID, newObs("Form.1/1-0", "c1")));
            fail("expected a conflict");
        } catch (FormObsConflictException e) {
            assertEquals(FormObsConflictException.MESSAGE, e.getMessage());
            assertTrue(!e.getMessage().contains(":"));
        }
    }

    private Obs existingObs(String uuid, String formFieldPath, String conceptUuid) {
        Obs obs = obs(uuid, formFieldPath, conceptUuid);
        encounter.addObs(obs);
        return obs;
    }

    private Obs obs(String uuid, String formFieldPath, String conceptUuid) {
        Concept concept = new Concept();
        concept.setUuid(conceptUuid);
        Obs obs = new Obs();
        obs.setUuid(uuid);
        obs.setConcept(concept);
        obs.setFormField(NAMESPACE, formFieldPath);
        return obs;
    }

    private BahmniObservation newObs(String formFieldPath, String conceptUuid) {
        return obsWithUuid("incoming-" + formFieldPath + "-" + conceptUuid, formFieldPath, conceptUuid);
    }

    private BahmniObservation obsWithUuid(String uuid, String formFieldPath, String conceptUuid) {
        EncounterTransaction.Concept concept = new EncounterTransaction.Concept();
        concept.setUuid(conceptUuid);
        BahmniObservation observation = new BahmniObservation();
        observation.setUuid(uuid);
        observation.setConcept(concept);
        observation.setFormNamespace(NAMESPACE);
        observation.setFormFieldPath(formFieldPath);
        return observation;
    }

    private BahmniEncounterTransaction transaction(String encounterUuid, BahmniObservation... observations) {
        BahmniEncounterTransaction transaction = new BahmniEncounterTransaction();
        transaction.setEncounterUuid(encounterUuid);
        transaction.setObservations(Arrays.asList(observations));
        return transaction;
    }
}
