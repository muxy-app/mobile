import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { client } from '@/state';
import type { FileContent, FileEntry, FileStat } from '@/transport';

import {
  createFileClient,
  fileName,
  isImageFile,
  joinFilePath,
  pathAffectsDirectory,
} from './fileManager';

export type FileManagerRoute =
  | { name: 'browser' }
  | { name: 'preview'; entry: FileEntry }
  | { name: 'move'; paths: string[] };

export type PreviewKind = 'text' | 'image' | 'unsupported';
export type FileManager = ReturnType<typeof useFileManager>;

const FILE_EVENT_DEBOUNCE_MS = 180;
const OWN_CHANGE_EVENT_WINDOW_MS = 1_000;
const ACTIVE_WORKTREE_CHANGED_ERROR = 'The active worktree changed. Discard this draft before continuing.';

export function useFileManager({
  projectId,
  worktreeId,
  visible,
}: {
  projectId: string;
  worktreeId?: string;
  visible: boolean;
}) {
  const fileClient = useMemo(() => createFileClient(client), []);
  const [route, setRoute] = useState<FileManagerRoute>({ name: 'browser' });
  const [currentPath, setCurrentPath] = useState('');
  const [entries, setEntries] = useState<FileEntry[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedPaths, setSelectedPaths] = useState<Set<string>>(() => new Set());
  const [content, setContent] = useState<FileContent | null>(null);
  const [fileStat, setFileStat] = useState<FileStat | null>(null);
  const [previewKind, setPreviewKind] = useState<PreviewKind>('text');
  const [fileLoading, setFileLoading] = useState(false);
  const [editing, setEditing] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [externalChanged, setExternalChanged] = useState(false);
  const [contextChanged, setContextChanged] = useState(false);
  const [busy, setBusy] = useState(false);
  const [movePath, setMovePath] = useState('');
  const [moveEntries, setMoveEntries] = useState<FileEntry[]>([]);
  const [moveLoading, setMoveLoading] = useState(false);

  const draftRef = useRef('');
  const routeRef = useRef(route);
  const currentPathRef = useRef(currentPath);
  const dirtyRef = useRef(dirty);
  const listRequestRef = useRef(0);
  const fileRequestRef = useRef(0);
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const ownChangeRef = useRef<{ paths: Set<string>; until: number } | null>(null);
  const entriesRef = useRef(entries);
  const visibleRef = useRef(false);
  const contextRef = useRef({ projectId, worktreeId });
  const contextChangedRef = useRef(false);

  useEffect(() => {
    routeRef.current = route;
  }, [route]);

  useEffect(() => {
    currentPathRef.current = currentPath;
  }, [currentPath]);

  useEffect(() => {
    dirtyRef.current = dirty;
  }, [dirty]);

  useEffect(() => {
    entriesRef.current = entries;
  }, [entries]);

  const loadDirectory = useCallback(
    async (path: string, mode: 'load' | 'refresh' | 'silent' = 'load') => {
      const requestId = ++listRequestRef.current;
      if (mode === 'load') setListLoading(true);
      if (mode === 'refresh') setRefreshing(true);
      if (mode !== 'silent') setError(null);
      try {
        const nextEntries = await fileClient.list(projectId, path);
        if (requestId !== listRequestRef.current) return;
        setEntries(nextEntries);
      } catch (nextError) {
        if (requestId !== listRequestRef.current) return;
        setError(nextError instanceof Error ? nextError.message : 'Couldn’t load this folder');
      } finally {
        if (requestId === listRequestRef.current) {
          setListLoading(false);
          setRefreshing(false);
        }
      }
    },
    [fileClient, projectId],
  );

  const loadMoveDirectory = useCallback(
    async (path: string) => {
      setMoveLoading(true);
      setError(null);
      try {
        const nextEntries = await fileClient.list(projectId, path);
        setMoveEntries(nextEntries.filter((entry) => entry.isDirectory));
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : 'Couldn’t load this folder');
      } finally {
        setMoveLoading(false);
      }
    },
    [fileClient, projectId],
  );

  const loadFile = useCallback(
    async (entry: FileEntry) => {
      const requestId = ++fileRequestRef.current;
      setFileLoading(true);
      setError(null);
      setContent(null);
      setFileStat(null);
      setEditing(false);
      setDirty(false);
      dirtyRef.current = false;
      setExternalChanged(false);
      draftRef.current = '';
      try {
        const stat = await fileClient.stat(projectId, entry.path);
        if (requestId !== fileRequestRef.current) return;
        setFileStat(stat);
        if (isImageFile(entry.path)) {
          const nextContent = await fileClient.read(projectId, entry.path, 'base64');
          if (requestId !== fileRequestRef.current) return;
          setPreviewKind('image');
          setContent(nextContent);
          return;
        }
        try {
          const nextContent = await fileClient.read(projectId, entry.path, 'utf8');
          if (requestId !== fileRequestRef.current) return;
          setPreviewKind('text');
          setContent(nextContent);
          draftRef.current = nextContent.content;
        } catch (readError) {
          if (requestId !== fileRequestRef.current) return;
          const message = readError instanceof Error ? readError.message : '';
          if (!message.toLowerCase().includes('utf-8') && !message.toLowerCase().includes('utf8')) {
            throw readError;
          }
          setPreviewKind('unsupported');
        }
      } catch (nextError) {
        if (requestId !== fileRequestRef.current) return;
        setError(nextError instanceof Error ? nextError.message : 'Couldn’t open this file');
      } finally {
        if (requestId === fileRequestRef.current) setFileLoading(false);
      }
    },
    [fileClient, projectId],
  );

  useEffect(() => {
    const previousContext = contextRef.current;
    const wasVisible = visibleRef.current;
    const changed = previousContext.projectId !== projectId || previousContext.worktreeId !== worktreeId;
    contextRef.current = { projectId, worktreeId };
    visibleRef.current = visible;
    if (!visible) {
      setContent(null);
      setFileStat(null);
      return;
    }
    if (wasVisible && changed && dirtyRef.current) {
      contextChangedRef.current = true;
      setContextChanged(true);
      setExternalChanged(false);
      return;
    }
    contextChangedRef.current = false;
    setContextChanged(false);
    setRoute({ name: 'browser' });
    setCurrentPath('');
    currentPathRef.current = '';
    setSelectionMode(false);
    setSelectedPaths(new Set());
    setEditing(false);
    setDirty(false);
    dirtyRef.current = false;
    setExternalChanged(false);
    loadDirectory('').catch(() => {});
  }, [visible, projectId, worktreeId, loadDirectory]);

  useEffect(() => {
    if (!visible) return;
    const off = client.on('fileChanged', (event) => {
      if (contextChangedRef.current) return;
      if (event.value.projectID !== projectId) return;
      if (worktreeId && event.value.worktreeID !== worktreeId) return;

      const previewRoute = routeRef.current;
      if (previewRoute.name === 'preview' && event.value.paths.includes(previewRoute.entry.path)) {
        const ownChange = ownChangeRef.current;
        const isOwnChange = ownChange?.paths.has(previewRoute.entry.path) && ownChange.until >= Date.now();
        if (!isOwnChange) {
          if (dirtyRef.current) setExternalChanged(true);
          else loadFile(previewRoute.entry).catch(() => {});
        } else {
          ownChangeRef.current = null;
        }
      }

      const directory = currentPathRef.current;
      const shouldRefresh = event.value.truncated || event.value.paths.some((path) => pathAffectsDirectory(path, directory));
      if (!shouldRefresh) return;
      if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
      refreshTimerRef.current = setTimeout(() => {
        refreshTimerRef.current = null;
        loadDirectory(currentPathRef.current, 'silent').catch(() => {});
      }, FILE_EVENT_DEBOUNCE_MS);
    });
    return () => {
      off();
      if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
    };
  }, [visible, projectId, worktreeId, loadDirectory, loadFile]);

  const goToDirectory = useCallback(
    (path: string) => {
      setCurrentPath(path);
      currentPathRef.current = path;
      setSelectionMode(false);
      setSelectedPaths(new Set());
      loadDirectory(path).catch(() => {});
    },
    [loadDirectory],
  );

  const openEntry = useCallback(
    (entry: FileEntry) => {
      if (selectionMode) {
        setSelectedPaths((current) => togglePath(current, entry.path));
        return;
      }
      if (entry.isDirectory) {
        goToDirectory(entry.path);
        return;
      }
      const nextRoute: FileManagerRoute = { name: 'preview', entry };
      setRoute(nextRoute);
      routeRef.current = nextRoute;
      loadFile(entry).catch(() => {});
    },
    [selectionMode, goToDirectory, loadFile],
  );

  const toggleSelection = useCallback((path: string) => {
    setSelectionMode(true);
    setSelectedPaths((current) => togglePath(current, path));
  }, []);

  const clearSelection = useCallback(() => {
    setSelectionMode(false);
    setSelectedPaths(new Set());
  }, []);

  const createFile = useCallback(
    async (name: string) => {
      setBusy(true);
      setError(null);
      try {
        const requestedPath = joinFilePath(currentPathRef.current, name.trim());
        const exists = entriesRef.current.some(
          (entry) => entry.name.toLocaleLowerCase() === name.trim().toLocaleLowerCase(),
        );
        if (exists) {
          setError(`“${name.trim()}” already exists in this folder.`);
          return;
        }
        const path = await fileClient.create(projectId, requestedPath);
        await loadDirectory(currentPathRef.current, 'silent');
        const entry: FileEntry = { name: fileName(path), path, isDirectory: false, isIgnored: false };
        const nextRoute: FileManagerRoute = { name: 'preview', entry };
        setRoute(nextRoute);
        routeRef.current = nextRoute;
        await loadFile(entry);
        setEditing(true);
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : 'Couldn’t create the file');
      } finally {
        setBusy(false);
      }
    },
    [fileClient, projectId, loadDirectory, loadFile],
  );

  const createFolder = useCallback(
    async (name: string) => {
      setBusy(true);
      setError(null);
      try {
        await fileClient.mkdir(projectId, joinFilePath(currentPathRef.current, name.trim()));
        await loadDirectory(currentPathRef.current, 'silent');
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : 'Couldn’t create the folder');
      } finally {
        setBusy(false);
      }
    },
    [fileClient, projectId, loadDirectory],
  );

  const rename = useCallback(
    async (path: string, newName: string) => {
      if (contextChangedRef.current) {
        setError(ACTIVE_WORKTREE_CHANGED_ERROR);
        return false;
      }
      setBusy(true);
      setError(null);
      ownChangeRef.current = { paths: new Set([path]), until: Date.now() + OWN_CHANGE_EVENT_WINDOW_MS };
      try {
        await fileClient.rename(projectId, path, newName.trim());
        clearSelection();
        await loadDirectory(currentPathRef.current, 'silent');
        return true;
      } catch (nextError) {
        ownChangeRef.current = null;
        setError(nextError instanceof Error ? nextError.message : 'Couldn’t rename this item');
        return false;
      } finally {
        setBusy(false);
      }
    },
    [fileClient, projectId, clearSelection, loadDirectory],
  );

  const deletePaths = useCallback(
    async (paths: string[]) => {
      if (contextChangedRef.current) {
        setError(ACTIVE_WORKTREE_CHANGED_ERROR);
        return;
      }
      setBusy(true);
      setError(null);
      ownChangeRef.current = { paths: new Set(paths), until: Date.now() + OWN_CHANGE_EVENT_WINDOW_MS };
      try {
        await fileClient.delete(projectId, paths);
        clearSelection();
        setRoute({ name: 'browser' });
        await loadDirectory(currentPathRef.current, 'silent');
      } catch (nextError) {
        ownChangeRef.current = null;
        setError(nextError instanceof Error ? nextError.message : 'Couldn’t delete these items');
      } finally {
        setBusy(false);
      }
    },
    [fileClient, projectId, clearSelection, loadDirectory],
  );

  const startMove = useCallback(
    (paths: string[]) => {
      const nextRoute: FileManagerRoute = { name: 'move', paths };
      setRoute(nextRoute);
      routeRef.current = nextRoute;
      setMovePath('');
      loadMoveDirectory('').catch(() => {});
    },
    [loadMoveDirectory],
  );

  const goToMoveDirectory = useCallback(
    (path: string) => {
      setMovePath(path);
      loadMoveDirectory(path).catch(() => {});
    },
    [loadMoveDirectory],
  );

  const moveHere = useCallback(async () => {
    const currentRoute = routeRef.current;
    if (currentRoute.name !== 'move') return;
    setBusy(true);
    setError(null);
    try {
      await fileClient.move(projectId, currentRoute.paths, movePath);
      clearSelection();
      const nextRoute: FileManagerRoute = { name: 'browser' };
      setRoute(nextRoute);
      routeRef.current = nextRoute;
      await loadDirectory(currentPathRef.current, 'silent');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Couldn’t move these items');
    } finally {
      setBusy(false);
    }
  }, [fileClient, projectId, movePath, clearSelection, loadDirectory]);

  const beginEditing = useCallback(() => {
    if (!content || previewKind !== 'text') return;
    draftRef.current = content.content;
    setDirty(false);
    dirtyRef.current = false;
    setEditing(true);
  }, [content, previewKind]);

  const updateDraft = useCallback(
    (value: string) => {
      draftRef.current = value;
      const nextDirty = value !== content?.content;
      dirtyRef.current = nextDirty;
      if (nextDirty !== dirty) setDirty(nextDirty);
    },
    [dirty, content],
  );

  const save = useCallback(async () => {
    const currentRoute = routeRef.current;
    if (currentRoute.name !== 'preview') return false;
    if (contextChangedRef.current) {
      setError(ACTIVE_WORKTREE_CHANGED_ERROR);
      return false;
    }
    setBusy(true);
    setError(null);
    ownChangeRef.current = {
      paths: new Set([currentRoute.entry.path]),
      until: Date.now() + OWN_CHANGE_EVENT_WINDOW_MS,
    };
    try {
      await fileClient.write(projectId, currentRoute.entry.path, draftRef.current);
      const updatedStat = await fileClient.stat(projectId, currentRoute.entry.path);
      setFileStat(updatedStat);
      setContent((current) => current ? { ...current, content: draftRef.current, size: updatedStat.size } : current);
      setDirty(false);
      dirtyRef.current = false;
      setEditing(false);
      setExternalChanged(false);
      await loadDirectory(currentPathRef.current, 'silent');
      return true;
    } catch (nextError) {
      ownChangeRef.current = null;
      setError(nextError instanceof Error ? nextError.message : 'Couldn’t save this file');
      return false;
    } finally {
      setBusy(false);
    }
  }, [fileClient, projectId, loadDirectory]);

  const discardDraft = useCallback(() => {
    draftRef.current = content?.content ?? '';
    setDirty(false);
    dirtyRef.current = false;
    setEditing(false);
    setExternalChanged(false);
  }, [content]);

  const keepEditingAfterExternalChange = useCallback(() => {
    setExternalChanged(false);
  }, []);

  const reloadOpenFile = useCallback(() => {
    const currentRoute = routeRef.current;
    if (currentRoute.name !== 'preview') return;
    loadFile(currentRoute.entry).catch(() => {});
  }, [loadFile]);

  const returnToBrowser = useCallback(() => {
    const shouldReloadRoot = contextChangedRef.current;
    const nextRoute: FileManagerRoute = { name: 'browser' };
    setRoute(nextRoute);
    routeRef.current = nextRoute;
    setContent(null);
    setFileStat(null);
    setEditing(false);
    setDirty(false);
    dirtyRef.current = false;
    setExternalChanged(false);
    contextChangedRef.current = false;
    setContextChanged(false);
    if (!shouldReloadRoot) return;
    setCurrentPath('');
    currentPathRef.current = '';
    setSelectionMode(false);
    setSelectedPaths(new Set());
    loadDirectory('').catch(() => {});
  }, [loadDirectory]);

  return {
    route,
    currentPath,
    entries,
    listLoading,
    refreshing,
    error,
    selectionMode,
    selectedPaths,
    content,
    fileStat,
    previewKind,
    fileLoading,
    editing,
    dirty,
    externalChanged,
    contextChanged,
    busy,
    movePath,
    moveEntries,
    moveLoading,
    refresh: () => loadDirectory(currentPathRef.current, 'refresh'),
    goToDirectory,
    openEntry,
    toggleSelection,
    clearSelection,
    setSelectionMode,
    createFile,
    createFolder,
    rename,
    deletePaths,
    startMove,
    goToMoveDirectory,
    moveHere,
    beginEditing,
    updateDraft,
    save,
    discardDraft,
    keepEditingAfterExternalChange,
    reloadOpenFile,
    returnToBrowser,
  };
}

function togglePath(current: Set<string>, path: string): Set<string> {
  const next = new Set(current);
  if (next.has(path)) next.delete(path);
  else next.add(path);
  return next;
}
