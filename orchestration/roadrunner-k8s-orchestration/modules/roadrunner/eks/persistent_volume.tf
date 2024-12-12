resource "kubernetes_persistent_volume_v1" "roadrunner_files_efs_pv" {
  metadata {
    name = "roadrunner-files-efs-pv"
  }

  spec {
    capacity  = {
      storage = "100Gi"
    }
    access_modes                     = ["ReadWriteMany"]
    persistent_volume_reclaim_policy = "Retain"
    storage_class_name               = kubernetes_storage_class.roadrunner_files_efs.metadata[0].name

    persistent_volume_source {
      csi {
        driver        = "efs.csi.aws.com"
        volume_handle = "${aws_efs_file_system.roadrunner_files.id}::${aws_efs_access_point.roadrunner_files.id}"
      }
    }
  }
}

