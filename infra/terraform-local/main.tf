data "kubernetes_namespace" "questify" {
  metadata {
    name = var.kubernetes_namespace
  }
}

resource "kubernetes_secret_v1" "quest_service_app_yml" {
  metadata {
    name      = "quest-service-app-yml"
    namespace = var.kubernetes_namespace
  }
  type = "Opaque"
  lifecycle { ignore_changes = [data, binary_data] }
}

resource "kubernetes_secret_v1" "user_service_app_yml" {
  metadata {
    name      = "user-service-app-yml"
    namespace = var.kubernetes_namespace
  }
  type = "Opaque"
  lifecycle { ignore_changes = [data, binary_data] }
}

resource "kubernetes_secret_v1" "submission_service_app_yml" {
  metadata {
    name      = "submission-service-app-yml"
    namespace = var.kubernetes_namespace
  }
  type = "Opaque"
  lifecycle { ignore_changes = [data, binary_data] }
}

resource "kubernetes_secret_v1" "proof_service_app_yml" {
  metadata {
    name      = "proof-service-app-yml"
    namespace = var.kubernetes_namespace
  }
  type = "Opaque"
  lifecycle { ignore_changes = [data, binary_data] }
}

resource "kubernetes_service_v1" "mysql_external" {
  metadata {
    name      = "mysql"
    namespace = var.kubernetes_namespace
  }
  spec {
    type          = "ExternalName"
    external_name = "mysql.default.svc.cluster.local"
  }
  wait_for_load_balancer = false
}
