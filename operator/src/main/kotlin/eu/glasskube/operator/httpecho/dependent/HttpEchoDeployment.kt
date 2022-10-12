package eu.glasskube.operator.httpecho.dependent

import eu.glasskube.kubernetes.api.model.apps.deployment
import eu.glasskube.kubernetes.api.model.apps.selector
import eu.glasskube.kubernetes.api.model.apps.spec
import eu.glasskube.kubernetes.api.model.apps.template
import eu.glasskube.kubernetes.api.model.container
import eu.glasskube.kubernetes.api.model.metadata
import eu.glasskube.kubernetes.api.model.spec
import eu.glasskube.operator.httpecho.HttpEcho
import eu.glasskube.operator.httpecho.HttpEchoReconciler
import eu.glasskube.operator.httpecho.identifyingLabel
import eu.glasskube.operator.httpecho.resourceLabels
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent

@KubernetesDependent(labelSelector = HttpEchoReconciler.SELECTOR)
class HttpEchoDeployment : CRUDKubernetesDependentResource<Deployment, HttpEcho>(Deployment::class.java) {
    override fun desired(primary: HttpEcho, context: Context<HttpEcho>) = deployment {
        metadata {
            name = primary.metadata.name
            namespace = primary.metadata.namespace
            labels = primary.resourceLabels
        }
        spec {
            selector {
                matchLabels = mapOf(primary.identifyingLabel)
            }
            template {
                metadata {
                    labels = primary.resourceLabels
                }
                spec {
                    containers = listOf(
                        container {
                            name = "echo"
                            image = "hashicorp/http-echo:0.2.3"
                            args = listOf("-text=\"${primary.spec.text}\"")
                            ports = listOf(eu.glasskube.kubernetes.api.model.containerPort { containerPort = 80 })
                        }
                    )
                }
            }
        }
    }
}
