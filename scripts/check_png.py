"""Reject damaged PNG icons before producing Android or web packages."""

from pathlib import Path
import struct
import zlib


def check_png(path):
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("missing PNG signature")
    offset = 8
    image_data = bytearray()
    ended = False
    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError("truncated PNG chunk")
        kind = data[offset + 4:offset + 8]
        contents = data[offset + 8:end - 4]
        crc = struct.unpack_from(">I", data, end - 4)[0]
        if zlib.crc32(kind + contents) != crc:
            raise ValueError(f"invalid checksum in {kind!r}")
        if kind == b"IDAT":
            image_data.extend(contents)
        offset = end
        if kind == b"IEND":
            ended = True
            break
    if not ended or offset != len(data) or not image_data:
        raise ValueError("incomplete PNG")
    zlib.decompress(image_data)


if __name__ == "__main__":
    icons = [*Path("app/src/main/res").rglob("*.png"), *Path(".").glob("rush-*.png")]
    if not icons:
        raise SystemExit("No PNG icons found")
    for icon in icons:
        try:
            check_png(icon)
        except (ValueError, zlib.error) as error:
            raise SystemExit(f"Invalid icon {icon}: {error}") from error
    print(f"Validated {len(icons)} PNG icons")
