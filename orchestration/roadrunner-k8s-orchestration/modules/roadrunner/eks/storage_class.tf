resource "kubernetes_storage_class" "roadrunner_files_efs" {
  metadata {
    name = "roadrunner-files-efs-storage-class"
  }

  storage_provisioner  = "efs.csi.aws.com"
  volume_binding_mode  = "Immediate"
  reclaim_policy       = "Retain"
}

