resource "aws_efs_access_point" "roadrunner_files" {
  file_system_id = aws_efs_file_system.roadrunner_files.id # Specifies the EFS file system to attach to

  posix_user {
    uid = 1000
    gid = 1000
  }

  root_directory {
    path = "/roadrunner-files" # Ensures the root directory is created on the EFS file system

    creation_info {
      owner_uid = 1000
      owner_gid = 1000
      permissions = "0775" # Grants group read/write access to the directory
    }
  }
}

