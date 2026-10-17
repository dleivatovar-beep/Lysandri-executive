import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  CheckCircle2,
  ChevronDown,
  Clock3,
  CircleCheck,
  Inbox,
  LoaderCircle,
  Mail,
  MailCheck,
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
          setErrorMessage('');
        } catch (error) {
          const fallback = informationRequestService.getLocalRequests();
          if (fallback.length > 0) {
            setRequests(fallback);
            setErrorMessage('');
          } else {
            setRequests([]);
            setErrorMessage(
              getApiErrorMessage(error),
            );
          }
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
      <section className="lx-admin-page lx-admin-requests-page">
        <header className="lx-admin-pagehead">
          <div className="lx-admin-pagehead-copy">
            <div>
              <h1>
                Solicitudes de información
              </h1>

              <p>
                Revisa y administra las
                solicitudes enviadas por
                nuevos estudiantes.
              </p>
            </div>
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
        </header>

        <div className="lx-admin-metric-strip lx-admin-request-summary" aria-label="Resumen de solicitudes">
          <SummaryCard
            label="Total"
            value={requests.length}
            tone="cyan"
            icon={<Inbox />}
          />

          <SummaryCard
            label="Pendientes"
            value={pendingCount}
            tone="amber"
            icon={<Clock3 />}
          />

          <SummaryCard
            label="Contactadas"
            value={contactedCount}
            tone="indigo"
            icon={<MailCheck />}
          />

          <SummaryCard
            label="Cerradas"
            value={closedCount}
            tone="emerald"
            icon={<CircleCheck />}
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

        <div className="lx-admin-queue-toolbar">
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
                    aria-pressed={selectedFilter === filter.value}
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
          <div className="lx-admin-request-list">
            {filteredRequests.map(
              (request) => (
                <article
                  key={
                    request.idSolicitud
                  }
                  className="lx-admin-request-row"
                >
                  <div className="lx-admin-request-person">
                    <span className="lx-request-avatar" aria-hidden="true">
                      {request.nombreCompleto.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'S'}
                    </span>
                    <div className="lx-request-person-copy">
                      <h2>{request.nombreCompleto}</h2>
                      <span className="lx-request-id">Solicitud #{request.idSolicitud}</span>
                      <a href={`mailto:${request.email}`} className="lx-request-email">
                        <Mail aria-hidden="true" /> <span>{request.email}</span>
                      </a>
                    </div>
                  </div>

                  <div className="lx-admin-request-details">
                    {request.telefono ? (
                      <a href={`tel:${request.telefono}`}><Phone aria-hidden="true" /> <span>{request.telefono}</span></a>
                    ) : (
                      <span className="is-muted"><Phone aria-hidden="true" /> Sin teléfono registrado</span>
                    )}
                    <p><Clock3 aria-hidden="true" /> Recibida {formatDate(request.fechaSolicitud)}</p>
                  </div>

                  <div className="lx-admin-request-state">
                    <label htmlFor={`status-${request.idSolicitud}`}>Seguimiento</label>
                    <div className={`lx-request-state-control lx-request-state-${request.estado.toLowerCase()}`}>
                      <span className="lx-request-status-mark" aria-hidden="true" />
                      <select
                        id={`status-${request.idSolicitud}`}
                        value={request.estado}
                        disabled={updatingId === request.idSolicitud}
                        aria-label={`Estado de seguimiento de ${request.nombreCompleto}`}
                        onChange={(event) => void handleStatusChange(request.idSolicitud, event.target.value as SolicitudInformacionEstado)}
                      >
                        <option value="PENDIENTE">Pendiente</option>
                        <option value="CONTACTADA">Contactada</option>
                        <option value="CERRADA">Cerrada</option>
                      </select>
                      {updatingId === request.idSolicitud ? <LoaderCircle className="lx-request-saving" aria-label="Guardando estado" /> : <ChevronDown className="lx-request-chevron" aria-hidden="true" />}
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => void handleDelete(request)}
                    disabled={deletingId === request.idSolicitud}
                    aria-label={`Eliminar solicitud de ${request.nombreCompleto}`}
                    title="Eliminar solicitud"
                    className="lx-admin-request-delete"
                  >
                    {deletingId === request.idSolicitud ? <LoaderCircle aria-hidden="true" className="animate-spin" /> : <Trash2 aria-hidden="true" />}
                  </button>
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
  icon: React.ReactNode;
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
    icon,
  }) => {
    return (
      <article className={`lx-admin-metric lx-admin-request-metric lx-admin-request-metric-${tone}`}>
        <span className="lx-admin-metric-mark lx-admin-request-mark" aria-hidden="true">{icon}</span>
        <div className="lx-admin-metric-copy">
          <p>{label}</p>
          <p>{value}</p>
        </div>
      </article>
    );
  };
