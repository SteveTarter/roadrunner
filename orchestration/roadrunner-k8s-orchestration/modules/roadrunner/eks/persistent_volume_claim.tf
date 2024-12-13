resource "kubernetes_persistent_volume_claim" "roadrunner_files_efs_pvc" {
  metadata {
    name      = "roadrunner-files-efs-pvc" # Unique name for the Persistent Volume Claim (PVC)
    namespace = var.roadrunner_namespace # Specifies the namespace to organize the PVC
  }

  spec {
    access_modes = ["ReadWriteMany"] # Allows multiple nodes to read/write concurrently

    resources {
      requests = {
        storage = "100Gi" # Requests 100Gi of storage for the claim
      }
    }

    storage_class_name = kubernetes_storage_class.roadrunner_files_efs.metadata[0].name # Links the PVC to the specific storage class
  }
}

