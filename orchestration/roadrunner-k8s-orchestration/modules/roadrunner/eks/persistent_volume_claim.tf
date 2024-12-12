resource "kubernetes_persistent_volume_claim" "roadrunner_files_efs_pvc" {
  metadata {
    name      = "roadrunner-files-efs-pvc"
    namespace = var.roadrunner_namespace
  }

  spec {
    access_modes = ["ReadWriteMany"]
    resources {
      requests = {
        storage = "100Gi"
      }
    }
    storage_class_name = kubernetes_storage_class.roadrunner_files_efs.metadata[0].name
  }
}
