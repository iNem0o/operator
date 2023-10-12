package eu.glasskube.operator.apps.plane

import eu.glasskube.kubernetes.client.patchOrUpdateStatus
import eu.glasskube.operator.api.reconciler.getSecondaryResource
import eu.glasskube.operator.api.reconciler.informerEventSource
import eu.glasskube.operator.apps.plane.dependent.PlaneApiConfigMap
import eu.glasskube.operator.apps.plane.dependent.PlaneApiDeployment
import eu.glasskube.operator.apps.plane.dependent.PlaneApiService
import eu.glasskube.operator.apps.plane.dependent.PlaneBackendConfigMap
import eu.glasskube.operator.apps.plane.dependent.PlaneBackendSecret
import eu.glasskube.operator.apps.plane.dependent.PlaneBeatWorkerDeployment
import eu.glasskube.operator.apps.plane.dependent.PlaneFrontendConfigMap
import eu.glasskube.operator.apps.plane.dependent.PlaneFrontendDeployment
import eu.glasskube.operator.apps.plane.dependent.PlaneFrontendService
import eu.glasskube.operator.apps.plane.dependent.PlaneIngress
import eu.glasskube.operator.apps.plane.dependent.PlanePostgresBackup
import eu.glasskube.operator.apps.plane.dependent.PlanePostgresCluster
import eu.glasskube.operator.apps.plane.dependent.PlanePostgresMinioBucket
import eu.glasskube.operator.apps.plane.dependent.PlaneRedisDeployment
import eu.glasskube.operator.apps.plane.dependent.PlaneRedisService
import eu.glasskube.operator.apps.plane.dependent.PlaneSpaceConfigMap
import eu.glasskube.operator.apps.plane.dependent.PlaneSpaceDeployment
import eu.glasskube.operator.apps.plane.dependent.PlaneSpaceService
import eu.glasskube.operator.apps.plane.dependent.PlaneWorkerConfigMap
import eu.glasskube.operator.apps.plane.dependent.PlaneWorkerDeployment
import eu.glasskube.operator.generic.BaseReconciler
import eu.glasskube.operator.generic.condition.isReady
import eu.glasskube.operator.infra.postgres.PostgresCluster
import eu.glasskube.operator.infra.postgres.isReady
import eu.glasskube.operator.webhook.WebhookService
import eu.glasskube.utils.logger
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent
import io.javaoperatorsdk.operator.processing.event.source.EventSource
import java.util.Optional
import kotlin.jvm.optionals.getOrDefault

@ControllerConfiguration(
    dependents = [
        Dependent(type = PlaneIngress::class, name = "PlaneIngress"),
        Dependent(type = PlaneBackendSecret::class, name = "PlaneBackendSecret"),
        Dependent(
            type = PlanePostgresMinioBucket::class,
            name = "PlanePostgresMinioBucket",
            reconcilePrecondition = PlanePostgresMinioBucket.ReconcilePrecondition::class
        ),
        Dependent(
            type = PlanePostgresCluster::class,
            name = "PlanePostgresCluster",
            readyPostcondition = PlanePostgresCluster.ReadyCondition::class
        ),
        Dependent(
            type = PlanePostgresBackup::class,
            name = "PlanePostgresBackup",
            dependsOn = ["PlanePostgresCluster"],
            reconcilePrecondition = PlanePostgresBackup.ReconcilePrecondition::class
        ),
        Dependent(
            type = PlaneRedisService::class,
            name = "PlaneRedisService",
            useEventSourceWithName = PlaneReconciler.SERVICE_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneRedisDeployment::class,
            name = "PlaneRedisDeployment",
            useEventSourceWithName = PlaneReconciler.DEPLOYMENT_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneApiConfigMap::class,
            name = "PlaneApiConfigMap",
            useEventSourceWithName = PlaneReconciler.CONFIGMAP_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneBackendConfigMap::class,
            name = "PlaneBackendConfigMap",
            useEventSourceWithName = PlaneReconciler.CONFIGMAP_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneApiService::class,
            name = "PlaneApiService",
            useEventSourceWithName = PlaneReconciler.SERVICE_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneApiDeployment::class,
            name = "PlaneApiDeployment",
            readyPostcondition = PlaneApiDeployment.ReadyCondition::class,
            dependsOn = ["PlanePostgresCluster", "PlaneRedisDeployment", "PlaneApiConfigMap", "PlaneBackendConfigMap", "PlaneBackendSecret"],
            useEventSourceWithName = PlaneReconciler.DEPLOYMENT_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneFrontendConfigMap::class,
            name = "PlaneFrontendConfigMap",
            useEventSourceWithName = PlaneReconciler.CONFIGMAP_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneFrontendService::class,
            name = "PlaneFrontendService",
            useEventSourceWithName = PlaneReconciler.SERVICE_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneFrontendDeployment::class,
            name = "PlaneFrontendDeployment",
            dependsOn = ["PlaneFrontendConfigMap"],
            useEventSourceWithName = PlaneReconciler.DEPLOYMENT_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneSpaceConfigMap::class,
            name = "PlaneSpaceConfigMap",
            useEventSourceWithName = PlaneReconciler.CONFIGMAP_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneSpaceService::class,
            name = "PlaneSpaceService",
            useEventSourceWithName = PlaneReconciler.SERVICE_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneSpaceDeployment::class,
            name = "PlaneSpaceDeployment",
            dependsOn = ["PlaneSpaceConfigMap"],
            useEventSourceWithName = PlaneReconciler.DEPLOYMENT_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneBeatWorkerDeployment::class,
            name = "PlaneBeatWorkerDeployment",
            dependsOn = ["PlanePostgresCluster", "PlaneRedisDeployment", "PlaneBackendConfigMap", "PlaneBackendSecret", "PlaneApiDeployment"],
            useEventSourceWithName = PlaneReconciler.DEPLOYMENT_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneWorkerConfigMap::class,
            name = "PlaneWorkerConfigMap",
            useEventSourceWithName = PlaneReconciler.CONFIGMAP_EVENT_SOURCE
        ),
        Dependent(
            type = PlaneWorkerDeployment::class,
            name = "PlaneWorkerDeployment",
            dependsOn = ["PlanePostgresCluster", "PlaneRedisDeployment", "PlaneBackendConfigMap", "PlaneBackendSecret", "PlaneWorkerConfigMap", "PlaneApiDeployment"],
            useEventSourceWithName = PlaneReconciler.DEPLOYMENT_EVENT_SOURCE
        )
    ]
)
class PlaneReconciler(webhookService: WebhookService) :
    BaseReconciler<Plane>(webhookService), EventSourceInitializer<Plane> {

    override fun processReconciliation(resource: Plane, context: Context<Plane>) = with(context) {
        resource.patchOrUpdateStatus(
            PlaneStatus(
                frontend = getSecondaryResource(PlaneFrontendDeployment.Discriminator()).getComponentStatus(),
                space = getSecondaryResource(PlaneSpaceDeployment.Discriminator()).getComponentStatus(),
                api = getSecondaryResource(PlaneApiDeployment.Discriminator()).getComponentStatus(),
                beatWorker = getSecondaryResource(PlaneBeatWorkerDeployment.Discriminator()).getComponentStatus(),
                worker = getSecondaryResource(PlaneWorkerDeployment.Discriminator()).getComponentStatus(),
                redis = getSecondaryResource(PlaneRedisDeployment.Discriminator()).getComponentStatus(),
                database = getSecondaryResource<PostgresCluster>().getDatabaseStatus()
            )
        )
    }

    private fun Optional<Deployment>.getComponentStatus() =
        PlaneStatus.ComponentStatus(map { it.isReady }.getOrDefault(false))

    private fun Optional<PostgresCluster>.getDatabaseStatus() =
        PlaneStatus.ComponentStatus(map { it.isReady }.getOrDefault(false))

    override fun prepareEventSources(context: EventSourceContext<Plane>) = with(context) {
        mutableMapOf<String, EventSource>(
            SERVICE_EVENT_SOURCE to informerEventSource<Service>(),
            DEPLOYMENT_EVENT_SOURCE to informerEventSource<Deployment>(),
            CONFIGMAP_EVENT_SOURCE to informerEventSource<ConfigMap>()
        )
    }

    companion object {
        internal const val SERVICE_EVENT_SOURCE = "PlaneServiceEventSource"
        internal const val DEPLOYMENT_EVENT_SOURCE = "PlaneDeploymentEventSource"
        internal const val CONFIGMAP_EVENT_SOURCE = "PlaneConfigMapEventSource"

        private val log = logger()
    }
}
