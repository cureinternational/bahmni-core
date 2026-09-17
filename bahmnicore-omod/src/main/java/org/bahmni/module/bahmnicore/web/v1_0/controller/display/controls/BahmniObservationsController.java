package org.bahmni.module.bahmnicore.web.v1_0.controller.display.controls;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.bahmni.module.bahmnicore.dao.BahmniConceptDao;
import org.bahmni.module.bahmnicore.dao.VisitDao;
import org.bahmni.module.bahmnicore.extensions.BahmniExtensions;
import org.bahmni.module.bahmnicore.obs.ObservationsAdder;
import org.bahmni.module.bahmnicore.service.BahmniObsService;
import org.bahmni.module.bahmnicore.util.MiscUtils;
import org.bahmni.module.bahmnicore.web.contract.BahmniObservationsBatchRequest;
import org.bahmni.module.bahmnicore.web.contract.VisitObservationsResponse;
import org.bahmni.module.bahmnicore.web.v1_0.LocaleResolver;
import org.openmrs.Concept;
import org.openmrs.ConceptSearchResult;
import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.VisitService;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.openmrs.util.LocaleUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.bahmni.module.bahmnicore.web.v1_0.LocaleResolver.identifyLocale;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/bahmnicore/observations")
public class BahmniObservationsController extends BaseRestController {

    private static final String LATEST = "latest";
    private static final String INITIAL = "initial";
    private BahmniObsService bahmniObsService;
    private ConceptService conceptService;
    private VisitService visitService;
    private VisitDao visitDao;
    private BahmniConceptDao bahmniConceptDao;
    private BahmniExtensions bahmniExtensions;

    @Autowired
    public BahmniObservationsController(BahmniObsService bahmniObsService, ConceptService conceptService, VisitService visitService, VisitDao visitDao, BahmniConceptDao bahmniConceptDao, BahmniExtensions bahmniExtensions) {
        this.bahmniObsService = bahmniObsService;
        this.conceptService = conceptService;
        this.visitService = visitService;
        this.visitDao = visitDao;
        this.bahmniConceptDao = bahmniConceptDao;
        this.bahmniExtensions = bahmniExtensions;
    }

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public Collection<BahmniObservation> get(@RequestParam(value = "patientUuid", required = true) String patientUUID,
                                             @RequestParam(value = "concept", required = true) List<String> rootConceptNames,
                                             @RequestParam(value = "locale", required = false) String locale,
                                             @RequestParam(value = "scope", required = false) String scope,
                                             @RequestParam(value = "numberOfVisits", required = false) Integer numberOfVisits,
                                             @RequestParam(value = "obsIgnoreList", required = false) List<String> obsIgnoreList,
                                             @RequestParam(value = "filterObsWithOrders", required = false, defaultValue = "true") Boolean filterObsWithOrders ) throws ParseException {

        List<Concept> conceptList = searchConceptsByName(rootConceptNames, identifyLocale(locale));
        Collection<BahmniObservation> observations;
        if (ObjectUtils.equals(scope, LATEST)) {
            observations = bahmniObsService.getLatest(patientUUID, conceptList, numberOfVisits, obsIgnoreList, filterObsWithOrders, null);
        } else if (ObjectUtils.equals(scope, INITIAL)) {
            observations = bahmniObsService.getInitial(patientUUID, conceptList, numberOfVisits, obsIgnoreList, filterObsWithOrders, null);
        } else {
            observations = bahmniObsService.observationsFor(patientUUID, conceptList, numberOfVisits, obsIgnoreList, filterObsWithOrders, null, null, null);
        }

        sendObsToGroovyScript(getConceptNames(conceptList), observations);

        return observations;
    }

    @RequestMapping(value = "/batch", method = RequestMethod.POST)
    @ResponseBody
    public List<VisitObservationsResponse> getBatch(@RequestBody BahmniObservationsBatchRequest request) {
        List<VisitObservationsResponse> responses = new ArrayList<>();
        List<String> conceptNames = request.getConcept();
        List<String> obsIgnoreList = request.getObsIgnoreList();
        boolean filterObsWithOrders = request.getFilterObsWithOrders();
        String scope = request.getScope();

        if (request.getVisitUuids() == null || request.getVisitUuids().isEmpty()) {
            return responses;
        }

        List<Concept> concepts = resolveConceptsForBatch(conceptNames);
        List<Visit> visits = visitDao.getVisitsByUuids(request.getVisitUuids());

        Map<String, Collection<BahmniObservation>> observationsByVisitUuid =
                bahmniObsService.getObsByVisitsAndConcepts(visits, concepts, conceptNames, obsIgnoreList, filterObsWithOrders, scope);

        for (String visitUuid : request.getVisitUuids()) {
            Collection<BahmniObservation> observations = observationsByVisitUuid.getOrDefault(visitUuid, Collections.emptyList());
            responses.add(new VisitObservationsResponse(visitUuid, observations));
        }
        return responses;
    }

    private List<Concept> resolveConceptsForBatch(List<String> conceptNames) {
        if (CollectionUtils.isEmpty(conceptNames)) {
            return new ArrayList<>();
        }

        List<Concept> concepts = new ArrayList<>(bahmniConceptDao.getConceptsByFullySpecifiedName(conceptNames));

        Set<String> resolvedNamesLowerCase = new HashSet<>();
        for (Concept concept : concepts) {
            resolvedNamesLowerCase.add(concept.getName().getName().toLowerCase());
        }
        List<String> unresolvedNames = new ArrayList<>();
        for (String name : conceptNames) {
            if (!resolvedNamesLowerCase.contains(name.toLowerCase())) {
                unresolvedNames.add(name);
            }
        }
        if (!unresolvedNames.isEmpty()) {
            concepts.addAll(MiscUtils.getConceptsForNames(unresolvedNames, conceptService));
        }
        return concepts;
    }

    private List<Concept> searchConceptsByName(List<String> conceptNames, Locale searchLocale) {
        Set<Concept> conceptSet = new LinkedHashSet<>();
        if (CollectionUtils.isNotEmpty(conceptNames)) {
            List<Locale> localeList = Collections.singletonList(searchLocale);
            for (String name : conceptNames) {
                List<ConceptSearchResult> conceptsSearchResult = conceptService.getConcepts(name, localeList, false, null, null, null, null, null, 0, null);
                List<Concept> conceptsByName = conceptsSearchResult.stream().map(conceptSearchResult -> conceptSearchResult.getConcept()).collect(Collectors.toList());
                conceptSet.addAll(conceptsByName);
            }
        }
        return new ArrayList<>(conceptSet);
    }

    @RequestMapping(method = RequestMethod.GET, params = {"visitUuid"})
    @ResponseBody
    public Collection<BahmniObservation> get(@RequestParam(value = "visitUuid", required = true) String visitUuid,
                                             @RequestParam(value = "scope", required = false) String scope,
                                             @RequestParam(value = "concept", required = false) List<String> conceptNames,
                                             @RequestParam(value = "obsIgnoreList", required = false) List<String> obsIgnoreList,
                                             @RequestParam(value = "filterObsWithOrders", required = false, defaultValue = "true") Boolean filterObsWithOrders) {

        Visit visit = visitService.getVisitByUuid(visitUuid);
        if (ObjectUtils.equals(scope, INITIAL)) {
            return bahmniObsService.getInitialObsByVisit(visit, MiscUtils.getConceptsForNames(conceptNames, conceptService), obsIgnoreList, filterObsWithOrders);
        } else if (ObjectUtils.equals(scope, LATEST)) {
            return bahmniObsService.getLatestObsByVisit(visit, MiscUtils.getConceptsForNames(conceptNames, conceptService), obsIgnoreList, filterObsWithOrders);
        } else {
            // Sending conceptName and obsIgnorelist, kinda contradicts, since we filter directly on concept names (not on root concept)
            return bahmniObsService.getObservationForVisit(visitUuid, conceptNames, MiscUtils.getConceptsForNames(obsIgnoreList, conceptService), filterObsWithOrders, null);
        }
    }

    @RequestMapping(method = RequestMethod.GET, params = {"encounterUuid"})
    @ResponseBody
    public Collection<BahmniObservation> get(@RequestParam(value = "encounterUuid", required = true) String encounterUuid,
                                             @RequestParam(value = "concept", required = false) List<String> conceptNames) {
        return bahmniObsService.getObservationsForEncounter(encounterUuid, conceptNames);
    }

    @RequestMapping(method = RequestMethod.GET, params = {"patientProgramUuid"})
    @ResponseBody
    public Collection<BahmniObservation> get(@RequestParam(value = "patientProgramUuid", required = true) String patientProgramUuid,
                                             @RequestParam(value = "concept", required = false) List<String> rootConceptNames,
                                             @RequestParam(value = "scope", required = false) String scope,
                                             @RequestParam(value = "obsIgnoreList", required = false) List<String> obsIgnoreList) throws ParseException {
        
        Collection<BahmniObservation> observations;
        if (ObjectUtils.equals(scope, LATEST)) {
            observations = bahmniObsService.getLatestObservationsForPatientProgram(patientProgramUuid, rootConceptNames, obsIgnoreList);
        } else if (ObjectUtils.equals(scope, INITIAL)) {
            observations = bahmniObsService.getInitialObservationsForPatientProgram(patientProgramUuid, rootConceptNames, obsIgnoreList);
        } else {
            observations = bahmniObsService.getObservationsForPatientProgram(patientProgramUuid, rootConceptNames, obsIgnoreList);
        }
        sendObsToGroovyScript(rootConceptNames, observations);
        return observations;
    }

    @RequestMapping(method = RequestMethod.GET, params = {"observationUuid"})
    @ResponseBody
    public BahmniObservation get(@RequestParam(value = "observationUuid") String observationUuid,
                                 @RequestParam(value = "revision", required = false) String revision) {
        if (ObjectUtils.equals(revision, LATEST)) {
            return bahmniObsService.getRevisedBahmniObservationByUuid(observationUuid);
        }
        return bahmniObsService.getBahmniObservationByUuid(observationUuid);
    }

    private void sendObsToGroovyScript(List<String> questions, Collection<BahmniObservation> observations) throws ParseException {
        ObservationsAdder observationsAdder = (ObservationsAdder) bahmniExtensions.getExtension("observationsAdder", "CurrentMonthOfTreatment.groovy");
        if (observationsAdder != null)
            observationsAdder.addObservations(observations, questions);
    }

    private List<String> getConceptNames(Collection<Concept> concepts) {
        List<String> conceptNames = new ArrayList<>();
        for (Concept concept : concepts) {
            conceptNames.add(concept.getName().getName());
        }
        return conceptNames;
    }
}
