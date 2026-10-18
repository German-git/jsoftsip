#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="JSoftSIP"
APP_DIR="/opt/${APP_NAME}"
LAUNCHER="/usr/bin/${APP_NAME}"
DESKTOP_FILE="/usr/share/applications/jsoftsip.desktop"
ICON_FILE="/usr/share/icons/hicolor/64x64/apps/jsoftsip.png"
LICENSE_FILE="/usr/share/licenses/${APP_NAME}/LICENSE"

if [[ ${EUID} -ne 0 ]]; then
    echo "Ejecutá el desinstalador con permisos de administrador:"
    echo "  sudo $0"
    exit 1
fi

if [[ -L "${LAUNCHER}" ]]; then
    if [[ "$(readlink -- "${LAUNCHER}")" == "${APP_DIR}/bin/${APP_NAME}" ]]; then
        rm -f -- "${LAUNCHER}"
    else
        echo "Se conserva ${LAUNCHER}: apunta a otra instalación." >&2
    fi
elif [[ -e "${LAUNCHER}" ]]; then
    echo "Se conserva ${LAUNCHER}: no es el enlace instalado por JSoftSIP." >&2
fi

rm -rf -- "${APP_DIR}"
rm -f -- "${DESKTOP_FILE}" "${ICON_FILE}" "${LICENSE_FILE}"

echo "${APP_NAME} se desinstaló correctamente."
echo "Las configuraciones de usuario en XDG_CONFIG_HOME/jsoftsip o ~/.config/jsoftsip se conservaron."
