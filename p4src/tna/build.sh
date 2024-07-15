#!/bin/bash
# Copyright 2020-present Open Networking Foundation
# SPDX-License-Identifier: Apache-2.0

set -eu -o pipefail

# DIR is this file directory.
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
P4_SRC_DIR=${DIR}/..
ROOT_DIR="$( cd "${DIR}/../.." && pwd )"

PROFILE=$1
OTHER_PP_FLAGS=$2

# shellcheck source=.env
source "${ROOT_DIR}/.env"

# PWD is the directory where this script is called from (should be the root of
# this repo).
P4C_OUT=${ROOT_DIR}/p4src/tna/build/${PROFILE}
# Prevent the creation by docker run to avoid having root owner
mkdir -p "${P4C_OUT}"

# Where the compiler output should be placed to be included in the pipeconf.
DEST_DIR=${ROOT_DIR}/src/main/resources/p4c-out/${PROFILE}

SDE_VER="9.7.0"
BASE_P4C_CMD="/root/bf-sde-9.7.0/install/bin/bf-p4c"

SHOW_SENSITIVE_OUTPUT=${SHOW_SENSITIVE_OUTPUT:-"false"}

# shellcheck disable=SC2086
function base_build() {
  output_dir="${P4C_OUT}/sde_${SDE_VER//./_}"
  echo "*** Compiling profile '${PROFILE}'..."
  echo "*** Output in ${output_dir}"
  p4c_flags=""
  mkdir -p ${output_dir}
  COMPILE_P4C_CMD="$BASE_P4C_CMD \
    --arch tna -g --create-graphs --verbose 2 \
    -o ${output_dir} -I ${P4_SRC_DIR} \
    ${OTHER_PP_FLAGS} \
    ${p4c_flags} \
    --p4runtime-files ${output_dir}/p4info.txt \
    --p4runtime-force-std-externs \
    ${DIR}/fabric_tna.p4"
  ssh $SWITCH_ADDR "rm -fr p4src"
  scp -r $ROOT_DIR/p4src $SWITCH_ADDR:~/
  ssh $SWITCH_ADDR "$(echo "mkdir -p ${output_dir} && ${COMPILE_P4C_CMD}" | sed "s|${ROOT_DIR}|/root|g")"
  scp -r $SWITCH_ADDR:~/p4src/tna/build/${PROFILE}/sde_9_7_0 $P4C_OUT
  sed -i "s|/root|${ROOT_DIR}|g" $output_dir/fabric_tna.conf

  # Generate the pipeline config binary
  docker run --rm -v "${output_dir}:${output_dir}" -w "${output_dir}" --user ${UID} \
    ${PIPELINE_CONFIG_BUILDER_IMG} \
    -p4c_conf_file=$output_dir/fabric_tna.conf \
    -bf_pipeline_config_binary_file=$output_dir/pipeline_config.pb.bin
}

function gen_profile() {
  output_dir="${P4C_OUT}/sde_${SDE_VER//./_}"
  pltf="$1_sde_${SDE_VER//./_}"

  # Copy only the relevant files to the pipeconf resources.
  mkdir -p "${DEST_DIR}/${pltf}"
  cp "${output_dir}/p4info.txt" "${DEST_DIR}/${pltf}"
  cp "${output_dir}/pipeline_config.pb.bin" "${DEST_DIR}/${pltf}/pipeline_config.pb.bin"
}

base_build
gen_profile "montara"
gen_profile "mavericks"
