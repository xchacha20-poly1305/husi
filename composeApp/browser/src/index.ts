export {createClashSession} from "./clash";
export {createRemoteServerStorage} from "./remote-server-storage";

export function saveFile(name: string, base64: string) {
    const bytes = Uint8Array.from(atob(base64), (value) => value.charCodeAt(0));
    const url = URL.createObjectURL(new Blob([bytes]));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = name;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function qrPng(value: string): Promise<string> {
    const {toDataURL} = await import("qrcode");
    return toDataURL(value, {width: 512, margin: 2});
}
