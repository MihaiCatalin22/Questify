terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "2.29.0"
    }
  }
  backend "local" {}
}

provider "kubernetes" {
  config_path    = pathexpand(var.kubeconfig_path != "" ? var.kubeconfig_path : "~/.kube/config")
  config_context = var.kube_context != "" ? var.kube_context : null
}
