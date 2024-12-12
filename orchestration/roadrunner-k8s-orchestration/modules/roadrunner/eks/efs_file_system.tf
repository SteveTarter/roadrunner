resource "aws_efs_file_system" "roadrunner_files" {
  creation_token = "roadrunner-files-efs"
  encrypted = true
  tags = {
    Name = "roadrunner-files-efs"
  }
}

