variable "kubernetes_namespace" {
  type    = string
  default = "questify"
}

variable "kubeconfig_path" {
  type    = string
  default = ""
}

variable "kube_context" {
  type    = string
  default = ""
}
