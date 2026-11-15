export {createClashSession} from "./clash";
export {createRemoteServerStorage} from "./remote-server-storage";

export async function qrPng(value: string): Promise<string> {
    const {toDataURL} = await import("qrcode");
    return toDataURL(value, {width: 512, margin: 2});
}
