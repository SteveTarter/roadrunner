resource "aws_efs_file_system" "roadrunner_files" {
  creation_token = "roadrunner-files-efs" # Unique identifier to ensure idempotency during creation
  encrypted = true # Encrypts data at rest to enhance security
  tags = {
    Name = "roadrunner-files-efs" # Tag for easier identification and management of the EFS file system
  }
}

