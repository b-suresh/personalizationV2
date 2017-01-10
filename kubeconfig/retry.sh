_retry() {
  [ -z "${2}" ] && return 1
  echo -n ${1}
  until printf "." && "${@:2}" &>/dev/null; do sleep 5.2; done; echo "✓"
}

echo "❤ Polling for cluster life - this could take a minute or more"

_retry "❤ Trying to connect to cluster with kubectl" kubectl cluster-info

kubectl cluster-info
