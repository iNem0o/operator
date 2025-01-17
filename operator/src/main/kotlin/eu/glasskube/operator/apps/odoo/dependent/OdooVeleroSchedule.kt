package eu.glasskube.operator.apps.odoo.dependent

import eu.glasskube.operator.apps.odoo.Odoo
import eu.glasskube.operator.generic.dependent.backups.DependentVeleroSchedule
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent

@KubernetesDependent
class OdooVeleroSchedule : DependentVeleroSchedule<Odoo>()
