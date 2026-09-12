"""Small stdio MCP client for inspecting the running Blender instance."""
import argparse
import asyncio
import base64
import json
import os
from datetime import timedelta
from pathlib import Path

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


async def run(args):
    environment = dict(os.environ, DISABLE_TELEMETRY="true")
    params = StdioServerParameters(
        command=r"C:\Users\Administrator\AppData\Local\Microsoft\WinGet\Links\uvx.exe",
        args=["--python", r"C:\Users\Administrator\AppData\Local\Programs\Python\Python311\python.exe", "blender-mcp"],
        env=environment,
    )
    arguments = json.loads(args.arguments)
    if args.script:
        arguments["code"] = Path(args.script).read_text(encoding="utf-8")
    arguments.setdefault("user_prompt", "根据照片在Blender中还原设备，并在模型里同步摄像头移动")
    async with stdio_client(params) as (reader, writer):
        async with ClientSession(reader, writer, read_timeout_seconds=timedelta(seconds=180)) as session:
            await session.initialize()
            result = await session.call_tool(args.tool, arguments)
            failed = bool(result.isError)
            for item in result.content:
                if item.type == "text":
                    print(item.text)
                    failed |= item.text.startswith("Error")
                elif item.type == "image" and args.image:
                    Path(args.image).write_bytes(base64.b64decode(item.data))
                    print("IMAGE:", str(Path(args.image).resolve()))
            if failed:
                raise SystemExit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("tool")
    parser.add_argument("--arguments", default="{}")
    parser.add_argument("--script")
    parser.add_argument("--image")
    asyncio.run(run(parser.parse_args()))
