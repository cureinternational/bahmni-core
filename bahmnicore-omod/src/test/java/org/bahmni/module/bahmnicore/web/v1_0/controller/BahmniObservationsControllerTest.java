package org.bahmni.module.bahmnicore.web.v1_0.controller;

import org.bahmni.module.bahmnicore.dao.VisitDao;
import org.bahmni.module.bahmnicore.extensions.BahmniExtensions;
import org.bahmni.module.bahmnicore.service.BahmniObsService;
import org.bahmni.module.bahmnicore.web.contract.BahmniObservationsBatchRequest;
import org.bahmni.module.bahmnicore.web.contract.VisitObservationsResponse;
import org.bahmni.module.bahmnicore.web.v1_0.controller.display.controls.BahmniObservationsController;
import org.bahmni.test.builder.VisitBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Concept;
import org.openmrs.Visit;
import org.openmrs.api.ConceptService;
import org.openmrs.api.VisitService;
import org.openmrs.module.bahmniemrapi.builder.BahmniObservationBuilder;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class BahmniObservationsControllerTest {

    @Mock
    private BahmniObsService bahmniObsService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private VisitService visitService;
    @Mock
    private VisitDao visitDao;
    @Mock
    private BahmniExtensions bahmniExtensions;

    private Visit visit;
    private Concept concept;
    private BahmniObservationsController bahmniObservationsController;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        visit = new VisitBuilder().withUUID("visitId").build();
        concept = new Concept();
        bahmniObservationsController = new BahmniObservationsController(bahmniObsService, conceptService, visitService, visitDao, bahmniExtensions);
        when(visitService.getVisitByUuid("visitId")).thenReturn(visit);
        when(visitDao.getVisitsByUuids(Arrays.asList("visitId"))).thenReturn(Arrays.asList(visit));
        when(conceptService.getConceptByName("Weight")).thenReturn(concept);
    }

    @Test
    public void returnLatestObservations() throws Exception {
        BahmniObservation latestObs = new BahmniObservation();
        latestObs.setUuid("initialId");
        when(bahmniObsService.getLatestObsByVisit(visit, Arrays.asList(concept), null, true)).thenReturn(Arrays.asList(latestObs));

        Collection<BahmniObservation> bahmniObservations = bahmniObservationsController.get("visitId", "latest", Arrays.asList("Weight"), null, true);

        verify(bahmniObsService, never()).getInitialObsByVisit(visit, Arrays.asList(concept), null, false);
        assertEquals(1, bahmniObservations.size());
    }

    @Test
    public void returnInitialObservation() throws Exception {
        EncounterTransaction.Concept cpt = new EncounterTransaction.Concept();
        cpt.setShortName("Concept1");

        BahmniObservation initialObs = new BahmniObservation();
        initialObs.setUuid("initialId");
        initialObs.setConcept(cpt);

        when(bahmniObsService.getInitialObsByVisit(visit, Arrays.asList(this.concept), null, true)).thenReturn(Arrays.asList(initialObs));

        Collection<BahmniObservation> bahmniObservations = bahmniObservationsController.get("visitId", "initial", Arrays.asList("Weight"), null, true);

        assertEquals(1, bahmniObservations.size());
    }

    @Test
    public void returnAllObservations() throws Exception {
        BahmniObservation obs = new BahmniObservation();
        List<String> conceptNames = Arrays.asList("Weight");
        ArrayList<Concept> obsIgnoreList = new ArrayList<>();
        when(bahmniObsService.getObservationForVisit("visitId", conceptNames, obsIgnoreList, true, null)).thenReturn(Arrays.asList(obs));

        Collection<BahmniObservation> bahmniObservations = bahmniObservationsController.get("visitId", null, conceptNames, null, true);

        verify(bahmniObsService, never()).getLatestObsByVisit(visit, Arrays.asList(concept), null, false);
        verify(bahmniObsService, never()).getInitialObsByVisit(visit, Arrays.asList(concept), null, false);
        verify(bahmniObsService, times(1)).getObservationForVisit("visitId", conceptNames, obsIgnoreList, true, null);

        assertEquals(1, bahmniObservations.size());
    }

    @Test
    public void shouldMakeACallToGetObsForEncounterAndConceptsSpecified() throws Exception {
        ArrayList<String> conceptNames = new ArrayList<>();
        String encounterUuid = "encounterUuid";
        String obsUuid = "ObsUuid";
        ArrayList<BahmniObservation> bahmniObservations = new ArrayList<>();
        bahmniObservations.add(new BahmniObservationBuilder().withUuid(obsUuid).build());
        when(bahmniObsService.getObservationsForEncounter(encounterUuid, conceptNames)).thenReturn(bahmniObservations);

        Collection<BahmniObservation> actualResult = bahmniObservationsController.get(encounterUuid, conceptNames);

        verify(bahmniObsService, times(1)).getObservationsForEncounter(encounterUuid, conceptNames);
        assertEquals(1, actualResult.size());
        assertEquals(obsUuid, actualResult.iterator().next().getUuid());
    }

    @Test
    public void shouldGetObsForPatientProgramWhenPatientProgramUuidIsSpecified() throws Exception {
        String patientProgramUuid = "patientProgramUuid";
        List<String> conceptNames = Arrays.asList("Weight");
        when(bahmniExtensions.getExtension("observationsAdder","CurrentMonthOfTreatment.groovy")).thenReturn(null);

        bahmniObservationsController.get(patientProgramUuid, conceptNames, null, null);

        verify(bahmniObsService, times(1)).getObservationsForPatientProgram(patientProgramUuid, conceptNames, null);
    }

    @Test
    public void shouldNotGetObsForPatientProgramWhenPatientProgramUuidIsSpecified() throws Exception {
        List<String> conceptNames = new ArrayList<String>();
        String patientProgramUuid = null;
        when(bahmniExtensions.getExtension("observationsAdder","CurrentMonthOfTreatment.groovy")).thenReturn(null);

        bahmniObservationsController.get(patientProgramUuid, null, null, null);

        verify(bahmniObsService, times(0)).getObservationsForPatientProgram(patientProgramUuid, conceptNames, null);
    }

    @Test
    public void shouldGetLatestObsForPatientProgramWhenPatientProgramUuidAndScopeLatestIsSpecified() throws Exception {
        List<String> conceptNames = new ArrayList<String>();
        List<String> ignoreObsList = new ArrayList<>();
        String patientProgramUuid = "patientProgramUuid";
        String scope = "latest";

        bahmniObservationsController.get(patientProgramUuid, conceptNames, scope, ignoreObsList);

        verify(bahmniObsService, times(1)).getLatestObservationsForPatientProgram(patientProgramUuid, conceptNames, ignoreObsList);
    }

    @Test
    public void shouldGetInitialObsForPatientProgramWhenPatientProgramUuidAndScopeLatestIsSpecified() throws Exception {
        List<String> conceptNames = new ArrayList<String>();
        List<String> ignoreObsList = new ArrayList<String>();
        String patientProgramUuid = "patientProgramUuid";
        String scope = "initial";
        when(bahmniExtensions.getExtension("observationsAdder","CurrentMonthOfTreatment.groovy")).thenReturn(null);

        bahmniObservationsController.get(patientProgramUuid, conceptNames, scope, ignoreObsList);

        verify(bahmniObsService, times(1)).getInitialObservationsForPatientProgram(patientProgramUuid, conceptNames, ignoreObsList);
    }

    @Test
    public void shouldGetBahmniObservationWithTheGivenObservationUuid() throws Exception {
        String observationUuid = "observationUuid";
        BahmniObservation expectedBahmniObservation = new BahmniObservation();
        when(bahmniObsService.getBahmniObservationByUuid(observationUuid)).thenReturn(expectedBahmniObservation);

        BahmniObservation actualBahmniObservation = bahmniObservationsController.get(observationUuid, "");

        verify(bahmniObsService, times(1)).getBahmniObservationByUuid("observationUuid");
        assertNotNull("BahmniObservation should not be null", actualBahmniObservation);
        assertEquals(expectedBahmniObservation, actualBahmniObservation);
    }

    @Test
    public void getBatch_shouldReturnObservationsGroupedByVisit() throws Exception {
        String visitUuid2 = "visitId2";
        Visit visit2 = new VisitBuilder().withUUID(visitUuid2).build();
        List<String> visitUuids = Arrays.asList("visitId", visitUuid2);
        when(visitDao.getVisitsByUuids(visitUuids)).thenReturn(Arrays.asList(visit, visit2));

        BahmniObservation obs1 = new BahmniObservationBuilder().withUuid("obs1").build();
        BahmniObservation obs2 = new BahmniObservationBuilder().withUuid("obs2").build();
        Map<String, Collection<BahmniObservation>> obsByVisitUuid = new HashMap<>();
        obsByVisitUuid.put("visitId", Arrays.asList(obs1));
        obsByVisitUuid.put(visitUuid2, Arrays.asList(obs2));
        when(bahmniObsService.getObsByVisitsAndConcepts(Arrays.asList(visit, visit2), new ArrayList<>(), null, null, true, null))
                .thenReturn(obsByVisitUuid);

        BahmniObservationsBatchRequest request = new BahmniObservationsBatchRequest();
        request.setVisitUuids(visitUuids);

        List<VisitObservationsResponse> responses = bahmniObservationsController.getBatch(request);

        assertEquals(2, responses.size());
        assertEquals("visitId", responses.get(0).getVisitUuid());
        assertEquals(1, responses.get(0).getObservations().size());
        assertEquals(visitUuid2, responses.get(1).getVisitUuid());
        assertEquals(1, responses.get(1).getObservations().size());
    }

    @Test
    public void getBatch_shouldResolveConceptsOnceAndDelegateToServiceWithScopeLatest() throws Exception {
        BahmniObservation latestObs = new BahmniObservationBuilder().withUuid("latestObs").build();
        Map<String, Collection<BahmniObservation>> obsByVisitUuid = new HashMap<>();
        obsByVisitUuid.put("visitId", Arrays.asList(latestObs));
        when(bahmniObsService.getObsByVisitsAndConcepts(Arrays.asList(visit), Arrays.asList(concept), Arrays.asList("Weight"), null, true, "latest"))
                .thenReturn(obsByVisitUuid);

        BahmniObservationsBatchRequest request = new BahmniObservationsBatchRequest();
        request.setVisitUuids(Arrays.asList("visitId"));
        request.setConcept(Arrays.asList("Weight"));
        request.setScope("latest");

        List<VisitObservationsResponse> responses = bahmniObservationsController.getBatch(request);

        assertEquals(1, responses.size());
        assertEquals("visitId", responses.get(0).getVisitUuid());
        assertEquals(1, responses.get(0).getObservations().size());
        verify(bahmniObsService, times(1)).getObsByVisitsAndConcepts(Arrays.asList(visit), Arrays.asList(concept), Arrays.asList("Weight"), null, true, "latest");
        verify(conceptService, times(1)).getConceptByName("Weight");
    }

    @Test
    public void getBatch_shouldResolveVisitsOnceViaBulkLookupRegardlessOfVisitCount() throws Exception {
        List<String> visitUuids = new ArrayList<>();
        List<Visit> visits = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String uuid = "visitId" + i;
            visitUuids.add(uuid);
            visits.add(new VisitBuilder().withUUID(uuid).build());
        }
        when(visitDao.getVisitsByUuids(visitUuids)).thenReturn(visits);
        when(bahmniObsService.getObsByVisitsAndConcepts(visits, new ArrayList<>(), null, null, true, null))
                .thenReturn(new HashMap<>());

        BahmniObservationsBatchRequest request = new BahmniObservationsBatchRequest();
        request.setVisitUuids(visitUuids);

        bahmniObservationsController.getBatch(request);

        verify(visitDao, times(1)).getVisitsByUuids(visitUuids);
        verify(visitService, never()).getVisitByUuid(anyString());
        verify(bahmniObsService, times(1)).getObsByVisitsAndConcepts(visits, new ArrayList<>(), null, null, true, null);
    }

    @Test
    public void getBatch_shouldReturnEmptyObservationsWhenServiceHasNoEntryForAVisit() throws Exception {
        when(bahmniObsService.getObsByVisitsAndConcepts(Arrays.asList(visit), new ArrayList<>(), null, null, true, null))
                .thenReturn(new HashMap<>());

        BahmniObservationsBatchRequest request = new BahmniObservationsBatchRequest();
        request.setVisitUuids(Arrays.asList("visitId"));

        List<VisitObservationsResponse> responses = bahmniObservationsController.getBatch(request);

        assertEquals(1, responses.size());
        assertEquals("visitId", responses.get(0).getVisitUuid());
        assertTrue(responses.get(0).getObservations().isEmpty());
    }

    @Test
    public void getBatch_shouldReturnEmptyListWhenVisitUuidsIsNull() throws Exception {
        BahmniObservationsBatchRequest request = new BahmniObservationsBatchRequest();
        request.setVisitUuids(null);

        List<VisitObservationsResponse> responses = bahmniObservationsController.getBatch(request);

        assertEquals(0, responses.size());
        verify(visitDao, never()).getVisitsByUuids(anyList());
        verify(bahmniObsService, never()).getObsByVisitsAndConcepts(any(List.class), any(List.class), any(List.class), any(List.class), any(Boolean.class), anyString());
    }

    @Test
    public void getBatch_shouldReturnEmptyListWhenVisitUuidsIsEmpty() throws Exception {
        BahmniObservationsBatchRequest request = new BahmniObservationsBatchRequest();
        request.setVisitUuids(new ArrayList<>());

        List<VisitObservationsResponse> responses = bahmniObservationsController.getBatch(request);

        assertEquals(0, responses.size());
        verify(visitDao, never()).getVisitsByUuids(anyList());
        verify(bahmniObsService, never()).getObsByVisitsAndConcepts(any(List.class), any(List.class), any(List.class), any(List.class), any(Boolean.class), anyString());
    }

}