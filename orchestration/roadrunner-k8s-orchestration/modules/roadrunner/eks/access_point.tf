resource "aws_efs_access_point" "roadrunner_files" {
  file_system_id = aws_efs_file_system.roadrunner_files.id

  posix_user {
    uid = 1000
    gid = 1000
  }

  root_directory {
    path = "/roadrunner-files"
    creation_info {
      owner_uid = 1000
      owner_gid = 1000
      permissions = "0775"
    }
  }
}

