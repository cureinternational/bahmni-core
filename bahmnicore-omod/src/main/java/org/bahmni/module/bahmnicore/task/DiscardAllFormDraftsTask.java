package org.bahmni.module.bahmnicore.task;

import org.bahmni.module.bahmnicore.service.FormDraftService;
import org.openmrs.api.context.Context;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscardAllFormDraftsTask extends AbstractTask {

    private static final Logger log = LoggerFactory.getLogger(DiscardAllFormDraftsTask.class);

    @Override
    public void execute() {
        try {
            log.info("DiscardAllFormDraftsTask: starting midnight task");

            FormDraftService formDraftService = Context.getService(FormDraftService.class);

            // Step 1: Discard all non-voided drafts (soft delete/void)
            log.debug("DiscardAllFormDraftsTask: discarding all non-voided drafts");
            formDraftService.discardAllDrafts();

            // Step 2: Delete drafts older than retention period (hard delete)
            log.debug("DiscardAllFormDraftsTask: deleting drafts older than retention period");
            formDraftService.deleteDraftsOlderThanRetentionPeriod();

            log.info("DiscardAllFormDraftsTask: completed successfully");
        } catch (Exception e) {
            log.error("DiscardAllFormDraftsTask: failed during execution", e);
        }
    }
}
