#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="JSoftSIP"
APP_DIR="/opt/${APP_NAME}"
LAUNCHER="/usr/bin/${APP_NAME}"
DESKTOP_FILE="/usr/share/applications/jsoftsip.desktop"
ICON_FILE="/usr/share/icons/hicolor/64x64/apps/jsoftsip.png"
LICENSE_FILE="/usr/share/licenses/${APP_NAME}/LICENSE"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if [[ ${EUID} -ne 0 ]]; then
    echo "Ejecutá el instalador con permisos de administrador:"
    echo "  sudo $0"
    exit 1
fi

for path in bin/JSoftSIP lib/JSoftSIP.png lib/runtime uninstall.sh jsoftsip.desktop LICENSE; do
    if [[ ! -e "${SCRIPT_DIR}/${path}" ]]; then
        echo "Falta un archivo requerido: ${path}" >&2
        exit 1
    fi
done

if [[ -L "${APP_DIR}" || ( -e "${APP_DIR}" && ! -d "${APP_DIR}" ) ]]; then
    echo "${APP_DIR} ya existe y no es un directorio de instalación válido." >&2
    exit 1
fi

if [[ -e "${LAUNCHER}" && ! -L "${LAUNCHER}" ]]; then
    echo "Ya existe un archivo en ${LAUNCHER}; no se sobrescribirá." >&2
    exit 1
fi

if [[ -L "${LAUNCHER}" && "$(readlink -- "${LAUNCHER}")" != "${APP_DIR}/bin/${APP_NAME}" ]]; then
    echo "${LAUNCHER} ya apunta a otra instalación; no se modificará." >&2
    exit 1
fi

for path in "${DESKTOP_FILE}" "${ICON_FILE}" "${LICENSE_FILE}"; do
    if [[ -L "${path}" ]]; then
        echo "${path} es un enlace simbólico; no se modificará." >&2
        exit 1
    fi
done

install -d -m 0755 "${APP_DIR}"
cp -a "${SCRIPT_DIR}/bin" "${SCRIPT_DIR}/lib" "${APP_DIR}/"
install -m 0755 "${SCRIPT_DIR}/uninstall.sh" "${APP_DIR}/uninstall.sh"

chown -R root:root "${APP_DIR}"
chmod -R a+rX,go-w "${APP_DIR}"

ln -sfnT "${APP_DIR}/bin/${APP_NAME}" "${LAUNCHER}"

install -Dm644 "${SCRIPT_DIR}/jsoftsip.desktop" "${DESKTOP_FILE}"
install -Dm644 "${APP_DIR}/lib/${APP_NAME}.png" "${ICON_FILE}"
install -Dm644 "${SCRIPT_DIR}/LICENSE" "${LICENSE_FILE}"

echo "${APP_NAME} se instaló correctamente."
echo "Ejecutá: ${APP_NAME}"
