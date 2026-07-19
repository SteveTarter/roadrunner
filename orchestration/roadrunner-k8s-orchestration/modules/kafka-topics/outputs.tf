output "topic_names" {
  description = "Map of Terraform topic keys to effective Kafka topic names"
  value = {
    for k, v in var.topics :
    k => coalesce(v.topic_name, k)
  }
}
