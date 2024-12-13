resource "kubernetes_storage_class" "roadrunner_files_efs" {
  metadata {
    name = "roadrunner-files-efs-storage-class" # Unique name for the storage class
  }

  storage_provisioner  = "efs.csi.aws.com" # Specifies the AWS EFS CSI driver as the provisioner
  volume_binding_mode  = "Immediate" # Allocates storage immediately upon PVC creation
  reclaim_policy       = "Retain" # Retains the volume and its data when the PVC is deleted
}

