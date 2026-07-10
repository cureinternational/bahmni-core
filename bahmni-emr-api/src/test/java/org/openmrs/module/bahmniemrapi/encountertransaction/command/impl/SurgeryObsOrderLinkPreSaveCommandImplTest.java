package org.openmrs.module.bahmniemrapi.encountertransaction.command.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openmrs.api.AdministrationService;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniEncounterTransaction;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SurgeryObsOrderLinkPreSaveCommandImplTest {

    private static final String SELECT_SURGERY_CONCEPT_UUID = "select-surgery-concept-uuid";
    private static final String SURGERY_ORDER_UUID = "surgery-order-uuid-123";
    private static final String OTHER_CONCEPT_UUID = "other-concept-uuid";

    @Mock
    private AdministrationService adminService;

    private SurgeryObsOrderLinkPreSaveCommandImpl command;

    @Before
    public void setUp() {
        command = new SurgeryObsOrderLinkPreSaveCommandImpl(adminService);
        when(adminService.getGlobalProperty(SurgeryObsOrderLinkPreSaveCommandImpl.SURGERY_SELECTION_CONCEPT_UUID_GP, ""))
                .thenReturn(SELECT_SURGERY_CONCEPT_UUID);
    }

    @Test
    public void shouldSetOrderUuidOnAllObsWhenSelectSurgeryObsPresent() {
        BahmniObservation selectSurgeryObs = obsWithConcept(SELECT_SURGERY_CONCEPT_UUID, SURGERY_ORDER_UUID);
        BahmniObservation careInstructionObs = obsWithConcept(OTHER_CONCEPT_UUID, "some value");

        BahmniEncounterTransaction transaction = new BahmniEncounterTransaction();
        transaction.setObservations(Arrays.asList(selectSurgeryObs, careInstructionObs));

        command.update(transaction);

        assertEquals(SURGERY_ORDER_UUID, selectSurgeryObs.getOrderUuid());
        assertEquals(SURGERY_ORDER_UUID, careInstructionObs.getOrderUuid());
    }

    @Test
    public void shouldNotOverwriteExistingOrderUuidOnObs() {
        String existingOrderUuid = "existing-order-uuid";
        BahmniObservation selectSurgeryObs = obsWithConcept(SELECT_SURGERY_CONCEPT_UUID, SURGERY_ORDER_UUID);
        BahmniObservation obsWithExistingOrder = obsWithConcept(OTHER_CONCEPT_UUID, "some value");
        obsWithExistingOrder.setOrderUuid(existingOrderUuid);

        BahmniEncounterTransaction transaction = new BahmniEncounterTransaction();
        transaction.setObservations(Arrays.asList(selectSurgeryObs, obsWithExistingOrder));

        command.update(transaction);

        assertEquals(existingOrderUuid, obsWithExistingOrder.getOrderUuid());
    }

    @Test
    public void shouldSkipWhenNoSelectSurgeryObsPresent() {
        BahmniObservation careInstructionObs = obsWithConcept(OTHER_CONCEPT_UUID, "some value");

        BahmniEncounterTransaction transaction = new BahmniEncounterTransaction();
        transaction.setObservations(Arrays.asList(careInstructionObs));

        command.update(transaction);

        assertNull(careInstructionObs.getOrderUuid());
    }

    @Test
    public void shouldSkipWhenGlobalPropertyNotConfigured() {
        when(adminService.getGlobalProperty(SurgeryObsOrderLinkPreSaveCommandImpl.SURGERY_SELECTION_CONCEPT_UUID_GP, ""))
                .thenReturn("");

        BahmniObservation selectSurgeryObs = obsWithConcept(SELECT_SURGERY_CONCEPT_UUID, SURGERY_ORDER_UUID);
        BahmniObservation careInstructionObs = obsWithConcept(OTHER_CONCEPT_UUID, "some value");

        BahmniEncounterTransaction transaction = new BahmniEncounterTransaction();
        transaction.setObservations(Arrays.asList(selectSurgeryObs, careInstructionObs));

        command.update(transaction);

        assertNull(careInstructionObs.getOrderUuid());
    }

    @Test
    public void shouldSetOrderUuidOnGroupMemberObs() {
        BahmniObservation selectSurgeryObs = obsWithConcept(SELECT_SURGERY_CONCEPT_UUID, SURGERY_ORDER_UUID);
        BahmniObservation childObs = obsWithConcept(OTHER_CONCEPT_UUID, "child value");
        BahmniObservation parentObs = obsWithConcept("parent-concept-uuid", null);
        parentObs.addGroupMember(childObs);

        BahmniEncounterTransaction transaction = new BahmniEncounterTransaction();
        transaction.setObservations(Arrays.asList(selectSurgeryObs, parentObs));

        command.update(transaction);

        assertEquals(SURGERY_ORDER_UUID, childObs.getOrderUuid());
        assertEquals(SURGERY_ORDER_UUID, parentObs.getOrderUuid());
    }

    @Test
    public void shouldSkipVoidedObs() {
        BahmniObservation selectSurgeryObs = obsWithConcept(SELECT_SURGERY_CONCEPT_UUID, SURGERY_ORDER_UUID);
        BahmniObservation voidedObs = obsWithConcept(OTHER_CONCEPT_UUID, "some value");
        voidedObs.setVoided(true);

        BahmniEncounterTransaction transaction = new BahmniEncounterTransaction();
        transaction.setObservations(Arrays.asList(selectSurgeryObs, voidedObs));

        command.update(transaction);

        assertNull(voidedObs.getOrderUuid());
    }

    private BahmniObservation obsWithConcept(String conceptUuid, Object value) {
        BahmniObservation obs = new BahmniObservation();
        EncounterTransaction.Concept concept = new EncounterTransaction.Concept();
        concept.setUuid(conceptUuid);
        obs.setConcept(concept);
        if (value != null) {
            obs.setValue(value);
        }
        obs.setConceptSortWeight(0);
        return obs;
    }
}
