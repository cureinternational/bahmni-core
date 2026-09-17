package org.bahmni.module.bahmnicore.service.impl;

import org.bahmni.module.bahmnicore.dao.ObsDao;
import org.bahmni.module.bahmnicore.dao.VisitDao;
import org.bahmni.module.bahmnicore.dao.impl.ObsDaoImpl;
import org.bahmni.module.bahmnicore.service.BahmniObsService;
import org.bahmni.module.bahmnicore.service.BahmniProgramWorkflowService;
import org.bahmni.test.builder.ConceptBuilder;
import org.bahmni.test.builder.VisitBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Visit;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ObsService;
import org.openmrs.api.VisitService;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.bahmniemrapi.encountertransaction.mapper.OMRSObsToBahmniObsMapper;
import org.openmrs.module.emrapi.encounter.matcher.ObservationTypeMatcher;
import org.openmrs.util.LocaleUtility;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(LocaleUtility.class)
public class BahmniObsServiceImplTest {

    private BahmniObsService bahmniObsService;

    private String personUUID = "12345";

    @Mock
    private ObsDao obsDao;
    @Mock
    private VisitDao visitDao;
    @Mock
    private ObservationTypeMatcher observationTypeMatcher;
    @Mock
    private VisitService visitService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private BahmniProgramWorkflowService bahmniProgramWorkflowService;
    @Mock
    private ObsService obsService;
    @Mock
    private OMRSObsToBahmniObsMapper omrsObsToBahmniObsMapper;

    @Before
    public void setUp() {
        initMocks(this);

        mockStatic(LocaleUtility.class);
        when(LocaleUtility.getDefaultLocale()).thenReturn(Locale.ENGLISH);
        when(observationTypeMatcher.getObservationType(any(Obs.class))).thenReturn(ObservationTypeMatcher.ObservationType.OBSERVATION);
        bahmniObsService = new BahmniObsServiceImpl(obsDao, omrsObsToBahmniObsMapper, visitService, conceptService, visitDao, bahmniProgramWorkflowService, obsService);
    }

    @Test
    public void shouldGetPersonObs() throws Exception {
        bahmniObsService.getObsForPerson(personUUID);
        verify(obsDao).getNumericObsByPerson(personUUID);
    }

    @Test
    public void shouldGetNumericConcepts() throws Exception {
        bahmniObsService.getNumericConceptsForPerson(personUUID);
        verify(obsDao).getNumericConceptsForPerson(personUUID);
    }

    @Test
    public void shouldGetObsByPatientUuidConceptNameAndNumberOfVisits() throws Exception {
        Concept bloodPressureConcept = new ConceptBuilder().withName("Blood Pressure").build();
        Integer numberOfVisits = 3;
        bahmniObsService.observationsFor(personUUID, asList(bloodPressureConcept), numberOfVisits, null, false, null, null, null);
        verify(obsDao).getObsByPatientAndVisit(personUUID, asList("Blood Pressure"),
                visitDao.getVisitIdsFor(personUUID, numberOfVisits), Integer.MAX_VALUE, ObsDaoImpl.OrderBy.DESC, null, false, null, null, null);
    }

    @Test
    public void shouldGetInitialObservations() throws Exception {
        Concept weightConcept = new ConceptBuilder().withName("Weight").build();
        Integer limit = 1;
        VisitBuilder visitBuilder = new VisitBuilder();
        Visit visit = visitBuilder.withUUID("visitId").withEncounter(new Encounter(1)).withPerson(new Person()).build();
        List<String> obsIgnoreList = new ArrayList<>();
        bahmniObsService.getInitialObsByVisit(visit, asList(weightConcept), obsIgnoreList, true);
        verify(obsDao).getObsByPatientAndVisit(visit.getPatient().getUuid(), asList("Weight"),
                asList(visit.getVisitId()), limit, ObsDaoImpl.OrderBy.ASC, obsIgnoreList, true, null, null, null);
    }

    @Test
    public void shouldGetAllObsForOrder() throws Exception {
        bahmniObsService.getObservationsForOrder("orderUuid");
        verify(obsDao, times(1)).getObsForOrder("orderUuid");
    }

    @Test
    public void shouldGetObsForPatientProgram() {
        Collection<Encounter> encounters = asList(new Encounter(), new Encounter());
        when(bahmniProgramWorkflowService.getEncountersByPatientProgramUuid(any(String.class))).thenReturn(encounters);
        Concept bloodPressureConcept = new ConceptBuilder().withName("Blood Pressure").build();
        Integer numberOfVisits = 3;

        bahmniObsService.observationsFor(personUUID, bloodPressureConcept, bloodPressureConcept, numberOfVisits, null, null, "patientProgramUuid");
        verify(obsDao).getObsFor(personUUID, bloodPressureConcept, bloodPressureConcept, visitDao.getVisitIdsFor(personUUID, numberOfVisits), encounters, null, null);
        verify(bahmniProgramWorkflowService).getEncountersByPatientProgramUuid("patientProgramUuid");
    }

    @Test
    public void shouldMakeACallToGetObservationsForEncounterAndConcepts() throws Exception {
        ArrayList<String> conceptNames = new ArrayList<>();
        String encounterUuid = "encounterUuid";

        bahmniObsService.getObservationsForEncounter(encounterUuid, conceptNames);

        verify(obsDao, times(1)).getObsForConceptsByEncounter(encounterUuid, conceptNames);
    }

    @Test
    public void shouldReturnEmptyObservationListIfProgramDoesNotHaveEncounters() {
        when(bahmniProgramWorkflowService.getEncountersByPatientProgramUuid(any(String.class))).thenReturn(EMPTY_LIST);
        Concept bloodPressureConcept = new ConceptBuilder().withName("Blood Pressure").build();

        Collection<BahmniObservation> observations = bahmniObsService.observationsFor(personUUID, bloodPressureConcept, bloodPressureConcept, 3, null, null, "patientProgramUuid");

        verify(obsDao, times(0)).getObsFor(anyString(), any(Concept.class), any(Concept.class), any(List.class), any(Collection.class), any(Date.class), any(Date.class));
        assertThat(observations.size(), is(equalTo(0)));
    }

    @Test
    public void shouldCallObsServiceWithEmptyListOfEncountersWhenProgramUuidIsNull() {
        Concept bloodPressureConcept = new ConceptBuilder().withName("Blood Pressure").build();

        int numberOfVisits = 3;
        bahmniObsService.observationsFor(personUUID, bloodPressureConcept, bloodPressureConcept, numberOfVisits, null, null, null);

        verify(obsDao).getObsFor(personUUID, bloodPressureConcept, bloodPressureConcept, visitDao.getVisitIdsFor(personUUID, numberOfVisits), new ArrayList<Encounter>(), null, null);
    }

    @Test
    public void shouldGetObsbyPatientProgramUuid() throws Exception {
        String patientProgramUuid = "patientProgramUuid";
        ArrayList<String> conceptNames = new ArrayList<>();
        List<Obs> obs = new ArrayList<>();
        conceptNames.add("Paracetamol");
        Collection<Concept> names = new ArrayList<Concept>() {{add(null);}};

        bahmniObsService.getObservationsForPatientProgram(patientProgramUuid, conceptNames, null);

        verify(obsDao).getObsByPatientProgramUuidAndConceptNames(patientProgramUuid, asList("Paracetamol"),  null, ObsDaoImpl.OrderBy.DESC, null, null);
        verify(omrsObsToBahmniObsMapper, times(1)).map(obs, names);
    }

    @Test
    public void shouldGetLatestObsbyPatientProgramUuid() throws Exception {
        String patientProgramUuid = "patientProgramUuid";
        List<String> conceptNames = new ArrayList<>();
        conceptNames.add("Paracetamol");
        List<Obs> obs = new ArrayList<>();
        Collection<Concept> names = new ArrayList<Concept>() {{add(null);}};

        bahmniObsService.getLatestObservationsForPatientProgram(patientProgramUuid, conceptNames, null);

        verify(obsDao).getObsByPatientProgramUuidAndConceptNames(patientProgramUuid, asList("Paracetamol"),  null, ObsDaoImpl.OrderBy.DESC, null, null);
        verify(omrsObsToBahmniObsMapper, times(1)).map(obs, names);
    }

    @Test
    public void shouldGetInitialObsbyPatientProgramUuid() throws Exception {
        String patientProgramUuid = "patientProgramUuid";
        List<String> conceptNames = new ArrayList<>();
        conceptNames.add("Paracetamol");
        List<Obs> obs = new ArrayList<>();
        Collection<Concept> names = new ArrayList<Concept>() {{add(null);}};

        bahmniObsService.getInitialObservationsForPatientProgram(patientProgramUuid, conceptNames, null);

        verify(obsDao).getObsByPatientProgramUuidAndConceptNames(patientProgramUuid, asList("Paracetamol"), 1, ObsDaoImpl.OrderBy.ASC, null, null);
        verify(omrsObsToBahmniObsMapper, times(1)).map(obs, names);
    }

    @Test
    public void shouldGetBahmniObservationByObservationUuid() throws Exception {
        String observationUuid = "observationUuid";
        Obs obs = new Obs();
        BahmniObservation expectedBahmniObservation = new BahmniObservation();
        when(obsService.getObsByUuid(observationUuid)).thenReturn(obs);
        when(omrsObsToBahmniObsMapper.map(obs, null)).thenReturn(expectedBahmniObservation);

        BahmniObservation actualBahmniObservation = bahmniObsService.getBahmniObservationByUuid(observationUuid);

        verify(obsService, times(1)).getObsByUuid(observationUuid);
        verify(omrsObsToBahmniObsMapper, times(1)).map(obs, null);
        assertNotNull(actualBahmniObservation);
        assertEquals(expectedBahmniObservation, actualBahmniObservation);
    }

    @Test
    public void shouldCallGetObsForFormBuilderFormsWithEncountersAndVisits() {
        String patientUuid = "patient-uuid";
        String patientProgramUuid = "patient-program-uuid";
        int numberOfVisits = 2;
        List<Integer> visitIds = asList(100, 101);
        List<String> formNames = singletonList("First Aid Form");
        List<Encounter> encounters = singletonList(mock(Encounter.class));

        when(bahmniProgramWorkflowService.getEncountersByPatientProgramUuid(patientProgramUuid))
                .thenReturn(encounters);
        when(visitDao.getVisitIdsFor(patientUuid, numberOfVisits)).thenReturn(visitIds);
        when(obsDao.getObsForFormBuilderForms(patientUuid, formNames, visitIds, encounters, null, null))
                .thenReturn(EMPTY_LIST);

        bahmniObsService.getObsForFormBuilderForms(patientUuid, formNames, numberOfVisits, null, null, patientProgramUuid);

        verify(bahmniProgramWorkflowService).getEncountersByPatientProgramUuid(patientProgramUuid);
        verify(visitDao).getVisitIdsFor(patientUuid, numberOfVisits);
        verify(obsDao).getObsForFormBuilderForms(patientUuid, formNames, visitIds, encounters, null, null);
    }

    @Test
    public void shouldReturnBahmniObservationWhenGetObsForFormBuilderFormsCalled() {
        String patientUuid = "patient-uuid";
        String patientProgramUuid = "patient-program-uuid";
        int numberOfVisits = 2;
        List<Integer> visitIds = asList(100, 101);
        List<String> formNames = singletonList("First Aid Form");
        List<Encounter> encounters = singletonList(mock(Encounter.class));
        Obs observation = mock(Obs.class);
        BahmniObservation bahmniObservation = mock(BahmniObservation.class);

        when(bahmniProgramWorkflowService.getEncountersByPatientProgramUuid(patientProgramUuid))
                .thenReturn(encounters);
        when(visitDao.getVisitIdsFor(patientUuid, numberOfVisits)).thenReturn(visitIds);
        when(obsDao.getObsForFormBuilderForms(patientUuid, formNames, visitIds, encounters, null, null))
                .thenReturn(singletonList(observation));
        when(omrsObsToBahmniObsMapper.map(observation, null)).thenReturn(bahmniObservation);

        Collection<BahmniObservation> bahmniObservations = bahmniObsService.getObsForFormBuilderForms(patientUuid,
                formNames, numberOfVisits, null, null, patientProgramUuid);

        assertEquals(1, bahmniObservations.size());
        assertEquals(bahmniObservation, bahmniObservations.iterator().next());
    }

    @Test
    public void getObsByVisitsAndConcepts_shouldCallObsDaoOncePerConceptRegardlessOfVisitCountForLatestScope() {
        List<Visit> visits = new ArrayList<>();
        List<Integer> visitIds = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Visit visit = new Visit(i);
            visit.setUuid("visit-" + i);
            visits.add(visit);
            visitIds.add(i);
        }
        Concept weight = new ConceptBuilder().withName("Weight").build();
        Concept pulse = new ConceptBuilder().withName("Pulse").build();
        List<Concept> concepts = asList(weight, pulse);

        when(obsDao.getObsByConceptAndVisits(anyString(), eq(visitIds), any(ObsDaoImpl.OrderBy.class), isNull(List.class), any(Boolean.class)))
                .thenReturn(new ArrayList<Obs>());

        bahmniObsService.getObsByVisitsAndConcepts(visits, concepts, null, null, true, "latest");

        verify(obsDao, times(1)).getObsByConceptAndVisits("Weight", visitIds, ObsDaoImpl.OrderBy.DESC, null, true);
        verify(obsDao, times(1)).getObsByConceptAndVisits("Pulse", visitIds, ObsDaoImpl.OrderBy.DESC, null, true);
        verify(obsDao, times(2)).getObsByConceptAndVisits(anyString(), eq(visitIds), any(ObsDaoImpl.OrderBy.class), isNull(List.class), any(Boolean.class));
        verify(obsDao, never()).getObsForVisits(any(List.class), any(ArrayList.class), any(List.class), any(Collection.class), any(Boolean.class), isNull(Order.class));
    }

    @Test
    public void getObsByVisitsAndConcepts_shouldUseAscendingOrderForInitialScope() {
        List<Visit> visits = asList(new Visit(1));
        visits.get(0).setUuid("visit-1");
        Concept weight = new ConceptBuilder().withName("Weight").build();

        when(obsDao.getObsByConceptAndVisits(anyString(), any(List.class), any(ObsDaoImpl.OrderBy.class), any(List.class), any(Boolean.class)))
                .thenReturn(new ArrayList<Obs>());

        bahmniObsService.getObsByVisitsAndConcepts(visits, asList(weight), null, null, true, "initial");

        verify(obsDao, times(1)).getObsByConceptAndVisits("Weight", asList(1), ObsDaoImpl.OrderBy.ASC, null, true);
    }

    @Test
    public void getObsByVisitsAndConcepts_shouldGroupObsPerVisitForLatestScope() {
        Visit visit1 = new Visit(1);
        visit1.setUuid("visit1");
        Visit visit2 = new Visit(2);
        visit2.setUuid("visit2");
        List<Visit> visits = asList(visit1, visit2);
        List<Integer> visitIds = asList(1, 2);

        Concept weight = new ConceptBuilder().withName("Weight").build();

        Obs obsForVisit1 = new Obs();
        Encounter encounter1 = new Encounter();
        encounter1.setVisit(visit1);
        obsForVisit1.setEncounter(encounter1);

        Obs obsForVisit2 = new Obs();
        Encounter encounter2 = new Encounter();
        encounter2.setVisit(visit2);
        obsForVisit2.setEncounter(encounter2);

        when(obsDao.getObsByConceptAndVisits("Weight", visitIds, ObsDaoImpl.OrderBy.DESC, null, true))
                .thenReturn(asList(obsForVisit1, obsForVisit2));

        BahmniObservation bahmniObs1 = new BahmniObservation();
        BahmniObservation bahmniObs2 = new BahmniObservation();
        when(omrsObsToBahmniObsMapper.map(asList(obsForVisit1), asList(weight))).thenReturn(asList(bahmniObs1));
        when(omrsObsToBahmniObsMapper.map(asList(obsForVisit2), asList(weight))).thenReturn(asList(bahmniObs2));

        Map<String, Collection<BahmniObservation>> result =
                bahmniObsService.getObsByVisitsAndConcepts(visits, asList(weight), null, null, true, "latest");

        assertEquals(2, result.size());
        assertEquals(1, result.get("visit1").size());
        assertEquals(bahmniObs1, result.get("visit1").iterator().next());
        assertEquals(1, result.get("visit2").size());
        assertEquals(bahmniObs2, result.get("visit2").iterator().next());
    }

    @Test
    public void getObsByVisitsAndConcepts_shouldCallObsForVisitsOnceRegardlessOfVisitCountForDefaultScope() {
        List<Visit> visits = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Visit visit = new Visit(i);
            visit.setUuid("visit-" + i);
            visit.setPatient(new Patient(new Person(i)));
            visit.setEncounters(new HashSet<Encounter>());
            visits.add(visit);
        }
        Concept weight = new ConceptBuilder().withName("Weight").build();

        when(obsDao.getObsForVisits(any(List.class), any(ArrayList.class), eq(asList(weight)), any(Collection.class), eq(true), isNull(Order.class)))
                .thenReturn(new ArrayList<Obs>());

        bahmniObsService.getObsByVisitsAndConcepts(visits, asList(weight), asList("Weight"), null, true, null);

        verify(obsDao, times(1)).getObsForVisits(any(List.class), any(ArrayList.class), eq(asList(weight)), any(Collection.class), eq(true), isNull(Order.class));
        verify(obsDao, never()).getObsByConceptAndVisits(anyString(), any(List.class), any(ObsDaoImpl.OrderBy.class), any(List.class), any(Boolean.class));
    }

    @Test
    public void getObsByVisitsAndConcepts_shouldReturnEmptyMapWhenNoVisitsGiven() {
        Map<String, Collection<BahmniObservation>> result =
                bahmniObsService.getObsByVisitsAndConcepts(new ArrayList<Visit>(), asList(new ConceptBuilder().withName("Weight").build()), null, null, true, "latest");

        assertEquals(0, result.size());
    }

    @Test
    public void getObsByVisitsAndConcepts_shouldReturnEmptyObservationsPerVisitWhenNoConceptsGiven() {
        Visit visit = new Visit(1);
        visit.setUuid("visit1");

        Map<String, Collection<BahmniObservation>> result =
                bahmniObsService.getObsByVisitsAndConcepts(asList(visit), new ArrayList<Concept>(), null, null, true, "latest");

        assertEquals(1, result.size());
        assertTrue(result.get("visit1").isEmpty());
        verify(obsDao, never()).getObsByConceptAndVisits(anyString(), any(List.class), any(ObsDaoImpl.OrderBy.class), any(List.class), any(Boolean.class));
    }
}
