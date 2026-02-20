resource "kubernetes_config_map" "roadrunner_view_config" {
  metadata {
    name      = "roadrunner-view-config"
    namespace = var.roadrunner_namespace
  }

  data = {
    REACT_APP_ROADRUNNER_REST_URL_BASE    = "${var.roadrunner_rest_url_base}"
    REACT_APP_PUBLIC_URL                  = "${var.roadrunner_view_url_base}"
    REACT_APP_MAPBOX_TOKEN                = "${var.mapbox_api_key}"
    REACT_APP_MAPBOX_MAP_STYLE            = "mapbox://styles/mapbox/streets-v12"
    REACT_APP_MAPBOX_API_URL              = "https://api.mapbox.com/"
    REACT_APP_LANDING_PAGE_URL            = "https://tarterware.com/"
    REACT_APP_COGNITO_REDIRECT_SIGN_IN    = "${var.cognito_redirect_sign_in}"
    REACT_APP_COGNITO_REDIRECT_SIGN_OUT   = "${var.cognito_redirect_sign_out}"
    REACT_APP_COGNITO_AUTHORITY           = "${var.cognito_authority}"
    REACT_APP_COGNITO_CLIENT_ID           = "${var.cognito_client_id}"
    REACT_APP_COGNITO_REDIRECT_URI        = "${var.cognito_redirect_uri}"
    REACT_APP_COGNITO_USER_POOL_ID        = "${var.cognito_user_pool_id}"
    REACT_APP_COGNITO_USER_POOL_CLIENT_ID = "${var.cognito_user_pool_client_id}"
    REACT_APP_COGNITO_DOMAIN              = "${var.cognito_domain}"
    
    
  }
}

