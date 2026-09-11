import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  CheckCircle2,
  Clock3,
  Inbox,
  LoaderCircle,
  Mail,
  Phone,
  RefreshCw,
  Search,
  ShieldAlert,
  Trash2,
} from 'lucide-react';

import { getApiErrorMessage } from '../../services/authService';

import {
  informationRequestService,
} from '../../services/informationRequestService';

import {
  SolicitudInformacionEstado,
  SolicitudInformacionResponse,
} from '../../types';

type RequestFilter =
  | 'TODAS'
  | SolicitudInformacionEstado;

const FILTERS: Array<{
  value: RequestFilter;
  label: string;
}> = [
  {
    value: 'TODAS',
    label: 'Todas',
  },
  {
    value: 'PENDIENTE',
    label: 'Pendientes',
  },
  {
    value: 'CONTACTADA',
    label: 'Contactadas',
  },
  {
    value: 'CERRADA',
    label: 'Cerradas',
  },
];

const STATUS_LABELS: Record<
  SolicitudInformacionEstado,
  string
> = {
  PENDIENTE: 'Pendiente',
  CONTACTADA: 'Contactada',
  CERRADA: 'Cerrada',
};

const STATUS_CLASSES: Record<
  SolicitudInformacionEstado,
  string
> = {
  PENDIENTE:
    'border-amber-500/20 bg-amber-500/10 text-amber-600 dark:text-amber-300',

  CONTACTADA:
    'border-cyan-500/20 bg-cyan-500/10 text-cyan-600 dark:text-cyan-300',

  CERRADA:
    'border-emerald-500/20 bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
};

const formatDate = (
  value: string,
): string => {
  if (!value) {
    return 'Sin fecha';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return 'Fecha no disponible';
  }

  return new Intl.DateTimeFormat(
    'es-PE',
    {
      dateStyle: 'medium',
      timeStyle: 'short',
    },
  ).format(date);
};

export const AdminInformationRequests:
  React.FC = () => {
    const [
      requests,
      setRequests,
    ] = useState<
      SolicitudInformacionResponse[]
    >([]);

    const [
      selectedFilter,
      setSelectedFilter,
    ] = useState<RequestFilter>(
      'TODAS',
    );

    const [search, setSearch] =
      useState('');

    const [
      isLoading,
      setIsLoading,
    ] = useState(true);

    const [
      updatingId,
      setUpdatingId,
    ] = useState<number | null>(
      null,
    );

    const [
      deletingId,
      setDeletingId,
    ] = useState<number | null>(
      null,
    );

    const [
      errorMessage,
      setErrorMessage,
    ] = useState('');

    const [
      successMessage,
      setSuccessMessage,
    ] = useState('');

    const loadRequests =
      useCallback(async () => {
        setIsLoading(true);
        setErrorMessage('');

        try {
          const response =
            await informationRequestService.getAll();

          setRequests(response);
        } catch (error) {
          setRequests([]);

          setErrorMessage(
            getApiErrorMessage(error),
          );
        } finally {
          setIsLoading(false);
        }
      }, []);

    useEffect(() => {
      void loadRequests();
    }, [loadRequests]);

    useEffect(() => {
      if (!successMessage) {
        return;
      }

      const timeout =
        window.setTimeout(() => {
          setSuccessMessage('');
        }, 4000);

      return () => {
        window.clearTimeout(
          timeout,
        );
      };
    }, [successMessage]);

    const filteredRequests =
      useMemo(() => {
        const normalizedSearch =
          search
            .trim()
            .toLowerCase();

        return requests.filter(
          (request) => {
            const matchesStatus =
              selectedFilter ===
                'TODAS' ||
              request.estado ===
                selectedFilter;

            const searchableText = [
              request.nombreCompleto,
              request.email,
              request.telefono,
            ]
              .join(' ')
              .toLowerCase();

            const matchesSearch =
              !normalizedSearch ||
              searchableText.includes(
                normalizedSearch,
              );

            return (
              matchesStatus &&
              matchesSearch
            );
          },
        );
      }, [
        requests,
        search,
        selectedFilter,
      ]);

    const pendingCount =
      requests.filter(
        (request) =>
          request.estado ===
          'PENDIENTE',
      ).length;

    const contactedCount =
      requests.filter(
        (request) =>
          request.estado ===
          'CONTACTADA',
      ).length;

    const closedCount =
      requests.filter(
        (request) =>
          request.estado ===
          'CERRADA',
      ).length;

    const handleStatusChange =
      async (
        requestId: number,
        estado:
          SolicitudInformacionEstado,
      ) => {
        setUpdatingId(requestId);
        setErrorMessage('');
        setSuccessMessage('');

        try {
          const updated =
            await informationRequestService
              .updateStatus(
                requestId,
                estado,
              );

          setRequests(
            (currentRequests) =>
              currentRequests.map(
                (request) =>
                  request.idSolicitud ===
                  requestId
                    ? updated
                    : request,
              ),
          );

          setSuccessMessage(
            'El estado de la solicitud fue actualizado.',
          );
        } catch (error) {
          setErrorMessage(
            getApiErrorMessage(error),
          );
        } finally {
          setUpdatingId(null);
        }
      };

    const handleDelete =
      async (
        request:
          SolicitudInformacionResponse,
      ) => {
        const confirmed =
          window.confirm(
            `¿Deseas eliminar la solicitud de ${request.nombreCompleto}?`,
          );

        if (!confirmed) {
          return;
        }

        setDeletingId(
          request.idSolicitud,
        );

        setErrorMessage('');
        setSuccessMessage('');

        try {
          await informationRequestService
            .remove(
              request.idSolicitud,
            );

          setRequests(
            (currentRequests) =>
              currentRequests.filter(
                (currentRequest) =>
                  currentRequest
                    .idSolicitud !==
                  request.idSolicitud,
              ),
          );

          setSuccessMessage(
            'La solicitud fue eliminada correctamente.',
          );
        } catch (error) {
          setErrorMessage(
            getApiErrorMessage(error),
          );
        } finally {
          setDeletingId(null);
        }
      };

    return (
      <section className="space-y-6">
        <div className="relative overflow-hidden rounded-3xl border border-cyan-500/15 bg-white p-7 dark:border-slate-800 dark:bg-[#0c111a] sm:p-8">
          <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-cyan-500/10 blur-3xl" />

          <div className="relative flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <span className="inline-flex rounded-full border border-cyan-500/20 bg-cyan-500/5 px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-cyan-600 dark:text-cyan-400">
                Administración
              </span>

              <h1 className="mt-5 text-3xl font-black text-slate-950 dark:text-white">
                Solicitudes de información
              </h1>

              <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-400">
                Revisa y administra las
                solicitudes enviadas por
                nuevos estudiantes.
              </p>
            </div>

            <button
              type="button"
              onClick={() =>
                void loadRequests()
              }
              disabled={isLoading}
              className="flex h-11 items-center justify-center gap-2 rounded-xl border border-slate-200 px-4 text-xs font-bold text-slate-600 transition-colors hover:border-cyan-500/40 hover:text-cyan-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:text-slate-300"
            >
              <RefreshCw
                className={`h-4 w-4 ${
                  isLoading
                    ? 'animate-spin'
                    : ''
                }`}
              />

              Actualizar
            </button>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <SummaryCard
            label="Total"
            value={requests.length}
            tone="cyan"
          />

          <SummaryCard
            label="Pendientes"
            value={pendingCount}
            tone="amber"
          />

          <SummaryCard
            label="Contactadas"
            value={contactedCount}
            tone="indigo"
          />

          <SummaryCard
            label="Cerradas"
            value={closedCount}
            tone="emerald"
          />
        </div>

        {errorMessage && (
          <div className="flex items-start gap-3 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-600 dark:text-rose-300">
            <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" />

            <span>
              {errorMessage}
            </span>
          </div>
        )}

        {successMessage && (
          <div className="flex items-start gap-3 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-600 dark:text-emerald-300">
            <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />

            <span>
              {successMessage}
            </span>
          </div>
        )}

        <div className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-[#0c111a]">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="relative w-full lg:max-w-md">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />

              <input
                type="search"
                value={search}
                onChange={(event) =>
                  setSearch(
                    event.target.value,
                  )
                }
                placeholder="Buscar por nombre, correo o teléfono..."
                className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-4 text-sm text-slate-900 outline-none placeholder:text-slate-400 focus:border-cyan-500/50 focus:ring-2 focus:ring-cyan-500/10 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
              />
            </div>

            <div className="flex flex-wrap gap-2">
              {FILTERS.map(
                (filter) => (
                  <button
                    key={filter.value}
                    type="button"
                    onClick={() =>
                      setSelectedFilter(
                        filter.value,
                      )
                    }
                    className={`rounded-xl border px-3 py-2 text-xs font-bold transition-colors ${
                      selectedFilter ===
                      filter.value
                        ? 'border-cyan-500/30 bg-cyan-500/10 text-cyan-600 dark:text-cyan-300'
                        : 'border-slate-200 text-slate-500 hover:border-cyan-500/20 hover:text-cyan-500 dark:border-slate-800'
                    }`}
                  >
                    {filter.label}
                  </button>
                ),
              )}
            </div>
          </div>
        </div>

        {isLoading ? (
          <div className="flex min-h-72 items-center justify-center">
            <LoaderCircle className="h-8 w-8 animate-spin text-cyan-500" />
          </div>
        ) : filteredRequests.length ===
          0 ? (
          <div className="flex min-h-72 flex-col items-center justify-center rounded-3xl border border-dashed border-cyan-500/20 bg-white p-8 text-center dark:border-slate-800 dark:bg-[#0c111a]">
            <span className="flex h-16 w-16 items-center justify-center rounded-2xl bg-cyan-500/10 text-cyan-500">
              <Inbox className="h-7 w-7" />
            </span>

            <h2 className="mt-5 text-lg font-black text-slate-950 dark:text-white">
              No hay solicitudes
            </h2>

            <p className="mt-2 max-w-md text-sm text-slate-500">
              No se encontraron solicitudes
              que coincidan con el filtro
              seleccionado.
            </p>
          </div>
        ) : (
          <div className="grid gap-5 xl:grid-cols-2">
            {filteredRequests.map(
              (request) => (
                <article
                  key={
                    request.idSolicitud
                  }
                  className="rounded-2xl border border-cyan-500/10 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0c111a]"
                >
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h2 className="font-black text-slate-950 dark:text-white">
                          {
                            request.nombreCompleto
                          }
                        </h2>

                        <span
                          className={`rounded-full border px-2.5 py-1 font-mono text-[9px] uppercase ${
                            STATUS_CLASSES[
                              request.estado
                            ]
                          }`}
                        >
                          {
                            STATUS_LABELS[
                              request.estado
                            ]
                          }
                        </span>
                      </div>

                      <p className="mt-1 font-mono text-[10px] text-slate-400">
                        Solicitud #
                        {
                          request.idSolicitud
                        }
                      </p>
                    </div>

                    <button
                      type="button"
                      onClick={() =>
                        void handleDelete(
                          request,
                        )
                      }
                      disabled={
                        deletingId ===
                        request.idSolicitud
                      }
                      aria-label="Eliminar solicitud"
                      className="flex h-9 w-9 items-center justify-center rounded-lg border border-rose-500/20 text-rose-500 transition-colors hover:bg-rose-500/10 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {deletingId ===
                      request.idSolicitud ? (
                        <LoaderCircle className="h-4 w-4 animate-spin" />
                      ) : (
                        <Trash2 className="h-4 w-4" />
                      )}
                    </button>
                  </div>

                  <div className="mt-5 space-y-3 text-sm text-slate-500 dark:text-slate-400">
                    <a
                      href={`mailto:${request.email}`}
                      className="flex items-center gap-3 transition-colors hover:text-cyan-500"
                    >
                      <Mail className="h-4 w-4 shrink-0 text-cyan-500" />

                      <span className="break-all">
                        {request.email}
                      </span>
                    </a>

                    {request.telefono ? (
                      <a
                        href={`tel:${request.telefono}`}
                        className="flex items-center gap-3 transition-colors hover:text-cyan-500"
                      >
                        <Phone className="h-4 w-4 shrink-0 text-indigo-500" />
                        <span>{request.telefono}</span>
                      </a>
                    ) : (
                      <span className="flex items-center gap-3 text-slate-400">
                        <Phone className="h-4 w-4 shrink-0 text-slate-400" />
                        <span>Sin teléfono registrado</span>
                      </span>
                    )}

                    <p className="flex items-center gap-3">
                      <Clock3 className="h-4 w-4 shrink-0 text-amber-500" />

                      <span>
                        Recibida el{' '}
                        {formatDate(
                          request.fechaSolicitud,
                        )}
                      </span>
                    </p>
                  </div>

                  <div className="mt-5 border-t border-slate-200 pt-4 dark:border-slate-800">
                    <label
                      htmlFor={`status-${request.idSolicitud}`}
                      className="mb-2 block text-xs font-bold text-slate-700 dark:text-slate-300"
                    >
                      Estado de la solicitud
                    </label>

                    <div className="relative">
                      <select
                        id={`status-${request.idSolicitud}`}
                        value={
                          request.estado
                        }
                        disabled={
                          updatingId ===
                          request.idSolicitud
                        }
                        onChange={(
                          event,
                        ) =>
                          void handleStatusChange(
                            request.idSolicitud,
                            event.target
                              .value as SolicitudInformacionEstado,
                          )
                        }
                        className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 pr-10 text-sm text-slate-900 outline-none focus:border-cyan-500/50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                      >
                        <option value="PENDIENTE">
                          Pendiente
                        </option>

                        <option value="CONTACTADA">
                          Contactada
                        </option>

                        <option value="CERRADA">
                          Cerrada
                        </option>
                      </select>

                      {updatingId ===
                        request.idSolicitud && (
                        <LoaderCircle className="pointer-events-none absolute right-9 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-cyan-500" />
                      )}
                    </div>
                  </div>
                </article>
              ),
            )}
          </div>
        )}
      </section>
    );
  };

interface SummaryCardProps {
  label: string;
  value: number;
  tone:
    | 'cyan'
    | 'amber'
    | 'indigo'
    | 'emerald';
}

const SummaryCard:
  React.FC<SummaryCardProps> = ({
    label,
    value,
    tone,
  }) => {
    const tones = {
      cyan: 'text-cyan-500 bg-cyan-500/10',
      amber:
        'text-amber-500 bg-amber-500/10',
      indigo:
        'text-indigo-500 bg-indigo-500/10',
      emerald:
        'text-emerald-500 bg-emerald-500/10',
    };

    return (
      <article className="flex items-center justify-between rounded-2xl border border-cyan-500/10 bg-white p-5 dark:border-slate-800 dark:bg-[#0c111a]">
        <div>
          <p className="text-xs text-slate-500">
            {label}
          </p>

          <p className="mt-2 font-mono text-2xl font-black text-slate-950 dark:text-white">
            {value}
          </p>
        </div>

        <span
          className={`flex h-11 w-11 items-center justify-center rounded-xl ${tones[tone]}`}
        >
          <Inbox className="h-5 w-5" />
        </span>
      </article>
    );
  };