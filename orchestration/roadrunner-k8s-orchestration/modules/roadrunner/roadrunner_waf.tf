resource "aws_wafv2_web_acl" "roadrunner_waf" {
  count       = terraform.workspace == "eks" ? 1 : 0
  name        = "roadrunner-waf"
  description = "WAF for securing the Roadrunner application edge"
  scope       = "REGIONAL" # Must be REGIONAL for Application Load Balancers (ALB)

  default_action {
    allow {}
  }

  # Add AWS Managed Rules for Core Protection (Mitigates XSS, SQLi, etc.)
  rule {
    name     = "AWS-AWSManagedRulesCommonRuleSet"
    priority = 1

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "AWSManagedRulesCommonRuleSetMetric"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "roadrunnerWafMetric"
    sampled_requests_enabled   = true
  }
}
