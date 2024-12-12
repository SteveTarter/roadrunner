resource "kubernetes_config_map" "roadrunner_view_config" {
  metadata {
    name      = "roadrunner-view-config"
    namespace = var.roadrunner_namespace
  }

  data = {
    REACT_APP_ROADRUNNER_REST_URL_BASE = "${var.roadrunner_rest_url_base}"
    REACT_APP_PUBLIC_URL               = "${var.roadrunner_view_url_base}"
    REACT_APP_AUTH0_DOMAIN             = "${var.auth0_api_domain}"
    REACT_APP_AUTH0_CLIENT_ID          = "${var.roadrunner_view_auth0_client_id}"
    REACT_APP_AUTH0_CLIENT_SECRET      = "${var.roadrunner_view_auth0_client_secret}"
    REACT_APP_AUTH0_CALLBACK_URL       = "${var.roadrunner_view_url_base}"
    REACT_APP_AUTH0_AUDIENCE           = "${var.tarterware_api_audience}"
    REACT_APP_MAPBOX_TOKEN             = "${var.mapbox_api_key}"
    REACT_APP_MAPBOX_MAP_STYLE         = "mapbox://styles/mapbox/streets-v12"
    REACT_APP_MAPBOX_API_URL           = "https://api.mapbox.com/"
    REACT_APP_LANDING_PAGE_URL         = "https://tarterware.com/"
  }
}

