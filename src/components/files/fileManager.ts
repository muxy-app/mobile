import type {
  FileContent,
  FileEncoding,
  FileEntry,
  FileStat,
  MethodParams,
  MethodResult,
} from '@/transport';

export type FileMethodName =
  | 'filesList'
  | 'filesRead'
  | 'filesStat'
  | 'filesWrite'
  | 'filesMkdir'
  | 'filesRename'
  | 'filesMove'
  | 'filesDelete';

export type FileTransport = {
  request<M extends FileMethodName>(method: M, params: MethodParams<M>): Promise<MethodResult<M>>;
};

export type FileClient = ReturnType<typeof createFileClient>;

export function createFileClient(transport: FileTransport) {
  return {
    async list(projectId: string, path: string): Promise<FileEntry[]> {
      const result = await transport.request('filesList', {
        type: 'filesList',
        value: { projectID: projectId, path: toServerPath(path) },
      });
      return result.value;
    },

    async read(projectId: string, path: string, encoding: FileEncoding): Promise<FileContent> {
      const result = await transport.request('filesRead', {
        type: 'filesRead',
        value: { projectID: projectId, path, encoding },
      });
      return result.value;
    },

    async stat(projectId: string, path: string): Promise<FileStat> {
      const result = await transport.request('filesStat', {
        type: 'filesStat',
        value: { projectID: projectId, path: toServerPath(path) },
      });
      return result.value;
    },

    async write(
      projectId: string,
      path: string,
      contents: string,
      encoding: FileEncoding = 'utf8',
    ): Promise<string> {
      const result = await transport.request('filesWrite', {
        type: 'filesWrite',
        value: { projectID: projectId, path, contents, encoding },
      });
      return result.value[0] ?? path;
    },

    async mkdir(projectId: string, path: string): Promise<string> {
      const result = await transport.request('filesMkdir', {
        type: 'filesMkdir',
        value: { projectID: projectId, path },
      });
      return result.value[0] ?? path;
    },

    async rename(projectId: string, path: string, newName: string): Promise<string> {
      const result = await transport.request('filesRename', {
        type: 'filesRename',
        value: { projectID: projectId, path, newName },
      });
      return result.value[0] ?? path;
    },

    async move(projectId: string, paths: string[], into: string): Promise<string[]> {
      const result = await transport.request('filesMove', {
        type: 'filesMove',
        value: { projectID: projectId, paths, into: toServerPath(into) },
      });
      return result.value;
    },

    async delete(projectId: string, paths: string[]): Promise<void> {
      await transport.request('filesDelete', {
        type: 'filesDelete',
        value: { projectID: projectId, paths },
      });
    },
  };
}

export type Breadcrumb = {
  label: string;
  path: string;
};

export function joinFilePath(parent: string, name: string): string {
  return parent ? `${parent}/${name}` : name;
}

export function fileName(path: string): string {
  const index = path.lastIndexOf('/');
  return index < 0 ? path : path.slice(index + 1);
}

export function parentFilePath(path: string): string {
  const index = path.lastIndexOf('/');
  return index < 0 ? '' : path.slice(0, index);
}

export function breadcrumbsForPath(path: string, rootLabel: string): Breadcrumb[] {
  const crumbs: Breadcrumb[] = [{ label: rootLabel, path: '' }];
  if (!path) return crumbs;
  let current = '';
  for (const part of path.split('/')) {
    current = joinFilePath(current, part);
    crumbs.push({ label: part, path: current });
  }
  return crumbs;
}

export function validateEntryName(value: string): string | null {
  const name = value.trim();
  if (!name) return 'Enter a name.';
  if (name === '.' || name === '..') return 'Choose another name.';
  if (name.includes('/') || name.includes('\\') || name.includes('\0')) {
    return 'Names cannot contain path separators.';
  }
  return null;
}

export function isImageFile(path: string): boolean {
  return ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'heic', 'heif'].includes(fileExtension(path));
}

export function imageMimeType(path: string): string {
  const extension = fileExtension(path);
  if (extension === 'jpg' || extension === 'jpeg') return 'image/jpeg';
  if (extension === 'gif') return 'image/gif';
  if (extension === 'webp') return 'image/webp';
  if (extension === 'bmp') return 'image/bmp';
  if (extension === 'heic' || extension === 'heif') return `image/${extension}`;
  return 'image/png';
}

export function fileExtension(path: string): string {
  const name = fileName(path);
  const index = name.lastIndexOf('.');
  return index > 0 ? name.slice(index + 1).toLowerCase() : '';
}

export function fileTypeLabel(path: string): string {
  const extension = fileExtension(path);
  if (!extension) return 'FILE';
  return extension.slice(0, 4).toUpperCase();
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${formatUnit(bytes / 1024)} KB`;
  return `${formatUnit(bytes / (1024 * 1024))} MB`;
}

export function pathAffectsDirectory(changedPath: string, directory: string): boolean {
  if (!directory) return !changedPath.includes('/');
  return parentFilePath(changedPath) === directory || changedPath === directory;
}

function toServerPath(path: string): string {
  return path || '.';
}

function formatUnit(value: number): string {
  return value >= 10 ? String(Math.round(value)) : value.toFixed(1);
}
