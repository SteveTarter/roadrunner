resource "kubernetes_persistent_volume_v1" "roadrunner_files_efs_pv" {
  metadata {
    name = "roadrunner-files-efs-pv" # Unique name to identify the Persistent Volume (PV)
  }

  spec {
    capacity  = {
      storage = "100Gi" # Allocates 100Gi of storage for the PV
    }
    access_modes                     = ["ReadWriteMany"] # Allows multiple nodes to read/write concurrently
    persistent_volume_reclaim_policy = "Retain" # Ensures data is preserved when the PV is released
    storage_class_name               = kubernetes_storage_class.roadrunner_files_efs.metadata[0].name # Links the PV to a specific storage class

    persistent_volume_source {
      csi {
        driver        = "efs.csi.aws.com" # Specifies the AWS EFS CSI driver
        volume_handle = "${aws_efs_file_system.roadrunner_files.id}::${aws_efs_access_point.roadrunner_files.id}" # Combines the EFS file system and access point for mounting
      }
    }
  }
}

