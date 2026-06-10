package org.openmrs.module.bahmniemrapi.encountertransaction.command.impl;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Provider;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniEncounterTransaction;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.openmrs.module.operationtheater.api.model.SurgicalAppointment;
import org.openmrs.module.operationtheater.api.service.SurgicalAppointmentService;
import org.openmrs.util.OpenmrsUtil;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@PrepareForTest({Context.class, OpenmrsUtil.class})
@RunWith(PowerMockRunner.class)
public class SurgeryOrderPostSaveCommandImplTest {

    @Mock private OrderService orderService;
    @Mock private ConceptService conceptService;
    @Mock private ProviderService providerService;
    @Mock private SessionFactory sessionFactory;
    @Mock private Session session;
    @Mock private Query query;
    @Mock private SurgicalAppointmentService surgicalAppointmentService;

    private SurgeryOrderPostSaveCommandImpl command;

    @Before
    public void setUp() {
        initMocks(this);
        mockStatic(OpenmrsUtil.class);
        mockStatic(Context.class);
        command = new SurgeryOrderPostSaveCommandImpl(orderService, conceptService, providerService, sessionFactory);

        when(sessionFactory.getCurrentSession()).thenReturn(session);
        when(session.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
    }

    @Test
    public void shouldCreateSurgeryOrderWhenSelectSurgeryObsPresent() {
        BahmniEncounterTransaction bet = new BahmniEncounterTransaction();
        bet.setObservations(Collections.singletonList(buildSelectSurgeryObs("appt-uuid-123")));

        OrderType surgeryOrderType = orderTypeWithName("Surgery Order");
        when(orderService.getOrderTypeByName("Surgery Order")).thenReturn(surgeryOrderType);
        when(orderService.saveOrder(any(Order.class), eq(null))).thenReturn(new Order());
        when(orderService.getCareSettingByName("OUTPATIENT")).thenReturn(new CareSetting());
        when(conceptService.getConceptByName("Select Surgery")).thenReturn(new Concept());

        SurgicalAppointment appt = new SurgicalAppointment();
        PowerMockito.when(Context.getService(SurgicalAppointmentService.class)).thenReturn(surgicalAppointmentService);
        when(surgicalAppointmentService.getSurgicalAppointmentByUuid("appt-uuid-123")).thenReturn(appt);

        command.save(bet, encounterWithProvider(), new EncounterTransaction());

        verify(orderService).getOrderTypeByName("Surgery Order");
        verify(orderService).saveOrder(any(Order.class), eq(null));
        verify(query).executeUpdate();
        verify(surgicalAppointmentService).save(appt);
    }

    @Test
    public void shouldCreateGeneralOrderWhenNoSelectSurgeryObs() {
        BahmniEncounterTransaction bet = new BahmniEncounterTransaction();
        bet.setObservations(Collections.singletonList(nonSurgeryObs()));

        when(orderService.getOrderTypeByName("General Order")).thenReturn(orderTypeWithName("General Order"));
        when(orderService.saveOrder(any(Order.class), eq(null))).thenReturn(new Order());
        when(orderService.getCareSettingByName("OUTPATIENT")).thenReturn(new CareSetting());
        when(conceptService.getConceptByName("Select Surgery")).thenReturn(new Concept());

        command.save(bet, encounterWithProvider(), new EncounterTransaction());

        verify(orderService).getOrderTypeByName("General Order");
        verify(orderService, never()).getOrderTypeByName("Surgery Order");
        verify(surgicalAppointmentService, never()).getSurgicalAppointmentByUuid(anyString());
    }

    @Test
    public void shouldReuseExistingSurgeryOrderAndLinkUnlinkedObs() {
        BahmniEncounterTransaction bet = new BahmniEncounterTransaction();
        bet.setObservations(Collections.singletonList(buildSelectSurgeryObs("appt-uuid-123")));

        Encounter encounter = encounterWithProvider();
        encounter.setOrders(Collections.singleton(existingOrderOfType("Surgery Order")));

        command.save(bet, encounter, new EncounterTransaction());

        verify(orderService, never()).saveOrder(any(Order.class), any());
        verify(query).executeUpdate();
    }

    @Test
    public void shouldReuseExistingGeneralOrderAndLinkUnlinkedObs() {
        BahmniEncounterTransaction bet = new BahmniEncounterTransaction();
        bet.setObservations(Collections.singletonList(nonSurgeryObs()));

        Encounter encounter = encounterWithProvider();
        encounter.setOrders(Collections.singleton(existingOrderOfType("General Order")));

        command.save(bet, encounter, new EncounterTransaction());

        verify(orderService, never()).saveOrder(any(Order.class), any());
        verify(query).executeUpdate();
    }

    @Test
    public void shouldCreateSurgeryOrderEvenWhenGeneralOrderExistsOnEncounter() {
        BahmniEncounterTransaction bet = new BahmniEncounterTransaction();
        bet.setObservations(Collections.singletonList(buildSelectSurgeryObs("appt-uuid-999")));

        Encounter encounter = encounterWithProvider();
        encounter.setOrders(Collections.singleton(existingOrderOfType("General Order")));

        OrderType surgeryType = orderTypeWithName("Surgery Order");
        when(orderService.getOrderTypeByName("Surgery Order")).thenReturn(surgeryType);
        when(orderService.saveOrder(any(Order.class), eq(null))).thenReturn(new Order());
        when(orderService.getCareSettingByName("OUTPATIENT")).thenReturn(new CareSetting());
        when(conceptService.getConceptByName("Select Surgery")).thenReturn(new Concept());

        SurgicalAppointment appt = new SurgicalAppointment();
        PowerMockito.when(Context.getService(SurgicalAppointmentService.class)).thenReturn(surgicalAppointmentService);
        when(surgicalAppointmentService.getSurgicalAppointmentByUuid("appt-uuid-999")).thenReturn(appt);

        command.save(bet, encounter, new EncounterTransaction());

        verify(orderService).saveOrder(any(Order.class), eq(null));
    }

    @Test
    public void shouldReturnGracefullyWhenOrderTypeNotConfigured() {
        BahmniEncounterTransaction bet = new BahmniEncounterTransaction();
        bet.setObservations(new ArrayList<>());

        when(orderService.getOrderTypeByName(any(String.class))).thenReturn(null);

        command.save(bet, encounterWithProvider(), new EncounterTransaction());

        verify(orderService, never()).saveOrder(any(Order.class), any());
        verify(query, never()).executeUpdate();
    }

    @Test
    public void shouldDetectSelectSurgeryObsNestedInGroupMember() {
        BahmniEncounterTransaction bet = new BahmniEncounterTransaction();

        BahmniObservation outer = new BahmniObservation();
        EncounterTransaction.Concept outerConcept = new EncounterTransaction.Concept();
        outerConcept.setName("Operative Report");
        outer.setConcept(outerConcept);
        List<BahmniObservation> groupMembers = new ArrayList<>();
        groupMembers.add(buildSelectSurgeryObs("nested-appt-uuid"));
        outer.setGroupMembers(groupMembers);
        bet.setObservations(Collections.singletonList(outer));

        when(orderService.getOrderTypeByName("Surgery Order")).thenReturn(orderTypeWithName("Surgery Order"));
        when(orderService.saveOrder(any(Order.class), eq(null))).thenReturn(new Order());
        when(orderService.getCareSettingByName("OUTPATIENT")).thenReturn(new CareSetting());
        when(conceptService.getConceptByName("Select Surgery")).thenReturn(new Concept());

        PowerMockito.when(Context.getService(SurgicalAppointmentService.class)).thenReturn(surgicalAppointmentService);
        when(surgicalAppointmentService.getSurgicalAppointmentByUuid("nested-appt-uuid"))
                .thenReturn(new SurgicalAppointment());

        command.save(bet, encounterWithProvider(), new EncounterTransaction());

        verify(orderService).getOrderTypeByName("Surgery Order");
        verify(surgicalAppointmentService).getSurgicalAppointmentByUuid("nested-appt-uuid");
    }

    // --- helpers ---

    private BahmniObservation buildSelectSurgeryObs(String apptUuid) {
        BahmniObservation obs = new BahmniObservation();
        EncounterTransaction.Concept concept = new EncounterTransaction.Concept();
        concept.setName("Select Surgery");
        obs.setConcept(concept);
        obs.setValue(apptUuid);
        return obs;
    }

    private BahmniObservation nonSurgeryObs() {
        BahmniObservation obs = new BahmniObservation();
        EncounterTransaction.Concept concept = new EncounterTransaction.Concept();
        concept.setName("Some Other Concept");
        obs.setConcept(concept);
        obs.setValue("some-value");
        return obs;
    }

    private OrderType orderTypeWithName(String name) {
        OrderType ot = new OrderType();
        ot.setName(name);
        return ot;
    }

    private Order existingOrderOfType(String typeName) {
        Order order = new Order();
        order.setOrderType(orderTypeWithName(typeName));
        order.setVoided(false);
        return order;
    }

    private Encounter encounterWithProvider() {
        Encounter encounter = new Encounter();
        Provider provider = new Provider();
        EncounterProvider ep = new EncounterProvider();
        ep.setProvider(provider);
        encounter.setEncounterProviders(Collections.singleton(ep));
        encounter.setOrders(new HashSet<>());
        return encounter;
    }
}
