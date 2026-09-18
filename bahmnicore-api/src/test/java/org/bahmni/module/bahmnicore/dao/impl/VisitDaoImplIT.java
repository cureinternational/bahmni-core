package org.bahmni.module.bahmnicore.dao.impl;

import org.bahmni.module.bahmnicore.BaseIntegrationTest;
import org.bahmni.module.bahmnicommons.api.dao.PatientDao;
import org.bahmni.module.bahmnicore.dao.VisitDao;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VisitDaoImplIT extends BaseIntegrationTest {
    
    @Autowired
    VisitDao visitDao;

    @Autowired
    PatientDao patientDao;

    @Autowired
    SessionFactory sessionFactory;

    @Before
    public void setUp() throws Exception {
        executeDataSet("visitTestData.xml");
    }

    @Test
    public void shouldGetLatestObsForConceptSetByVisit() {
        Visit latestVisit = visitDao.getLatestVisit("86526ed5-3c11-11de-a0ba-001e378eb67a", "Weight");
        assertEquals(901, latestVisit.getVisitId().intValue());
    }

    @Test
    public void shouldGetVisitsByPatient(){
        Patient patient = patientDao.getPatient("GAN200000");
        List<Visit> visits = visitDao.getVisitsByPatient(patient, 1);
        assertEquals(1, visits.size());
        assertEquals(901, visits.get(0).getVisitId().intValue());
    }

    @Test
    public void shouldNotGetVoidedEncounter() throws Exception {
        List<Encounter> admitAndDischargeEncounters = visitDao.getAdmitAndDischargeEncounters(902);
        assertEquals(1, admitAndDischargeEncounters.size());
    }

    @Test
    public void shouldGetMultipleVisitsInOneBulkCallByUuid() throws Exception {
        List<Visit> visits = visitDao.getVisitsByUuids(Arrays.asList(
                "ad41fb41-a41a-4ad6-8835-2f59099acf5t", "ad41fb41-a41a-4ad6-8835-2f59099acf5b"));

        assertEquals(2, visits.size());
        List<Integer> visitIds = Arrays.asList(visits.get(0).getVisitId(), visits.get(1).getVisitId());
        assertTrue(visitIds.contains(901));
        assertTrue(visitIds.contains(902));
    }

    @Test
    public void shouldFetchEncountersEagerlyWithoutOneExtraQueryPerVisit() throws Exception {
        List<String> visitUuids = Arrays.asList(
                "ad41fb41-a41a-4ad6-8835-2f59099acf5t", "ad41fb41-a41a-4ad6-8835-2f59099acf5b");

        sessionFactory.getStatistics().setStatisticsEnabled(true);
        sessionFactory.getStatistics().clear();

        List<Visit> visits = visitDao.getVisitsByUuids(visitUuids);
        for (Visit visit : visits) {
            visit.getEncounters().size();
        }

        long queryCount = sessionFactory.getStatistics().getQueryExecutionCount();

        assertEquals("visits + encounters should come back in one fetch-joined query, not one extra query per visit",
                1, queryCount);
        assertEquals(2, visits.size());
    }

    @Test
    public void shouldReturnEmptyListWhenNoUuidsGivenForBulkVisitLookup() throws Exception {
        assertEquals(0, visitDao.getVisitsByUuids(new java.util.ArrayList<>()).size());
    }
}