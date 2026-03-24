#! /bin/bash
# Use this command to clear hung namespace errors
kubectl patch kafkatopic vehicle-position-v1 -n roadrunner --type=merge -p '{"metadata":{"finalizers":[]}}'
