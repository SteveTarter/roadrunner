module "eks_roadrunner_files_resources" {
  source = "./eks"
  count  = terraform.workspace == "eks" ? 1 : 0

  region               = var.region
  cluster_name         = var.cluster_name
  roadrunner_namespace = var.roadrunner_namespace
  eks_vpc_name         = var.eks_vpc_name
  efs_sg_name          = var.efs_sg_name
}
