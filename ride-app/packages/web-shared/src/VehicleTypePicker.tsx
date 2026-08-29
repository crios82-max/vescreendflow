import type { RideEstimateOption, VehicleType } from '@ride-app/shared';
import { VEHICLE_OPTIONS } from '@ride-app/shared';
import { useI18n } from './I18nProvider';

interface VehicleTypePickerProps {
  options: RideEstimateOption[];
  selected: VehicleType;
  onSelect: (type: VehicleType) => void;
}

export function VehicleTypePicker({ options, selected, onSelect }: VehicleTypePickerProps) {
  const { t, vehicle } = useI18n();
  return (
    <div className="vehicle-picker">
      {options.map((option) => {
        const meta = VEHICLE_OPTIONS[option.vehicleType];
        const active = selected === option.vehicleType;
        const isDelivery = meta.category === 'delivery';
        return (
          <button
            key={option.vehicleType}
            type="button"
            className={`vehicle-card${active ? ' vehicle-card--active' : ''}`}
            onClick={() => onSelect(option.vehicleType)}
          >
            <span className="vehicle-card__icon">{meta.icon}</span>
            <span className="vehicle-card__body">
              <strong>{vehicle(option.vehicleType)}</strong>
              <span>
                {t(`vehicle.${option.vehicleType}.description`)}
                {isDelivery
                  ? ` · ${t('service.courier')}`
                  : ` · ${option.seats} ${t('common.pax')}`}
              </span>
            </span>
            <span className="vehicle-card__price">${option.estimatedPrice}</span>
          </button>
        );
      })}
    </div>
  );
}
