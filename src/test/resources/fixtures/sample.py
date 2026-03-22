"""Sample Python module for analysis testing."""

import os
import sys
from pathlib import Path
from typing import List, Optional


class FileProcessor:
    """Processes files in a directory."""

    def __init__(self, base_dir: str):
        self.base_dir = Path(base_dir)
        self._cache = {}

    def process(self, filename: str) -> Optional[str]:
        """Process a single file and return its content."""
        path = self.base_dir / filename
        if not path.exists():
            return None
        with open(path) as f:
            return f.read()

    def process_all(self) -> List[str]:
        """Process all files in the base directory."""
        results = []
        for f in os.listdir(self.base_dir):
            content = self.process(f)
            if content is not None:
                results.append(content)
        return results


def read_file(path: str) -> str:
    """Read and return file contents."""
    with open(path) as f:
        return f.read()


def write_file(path: str, content: str) -> None:
    """Write content to a file."""
    with open(path, "w") as f:
        f.write(content)
