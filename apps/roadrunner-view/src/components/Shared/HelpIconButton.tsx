import React from 'react';
import { Button } from 'react-bootstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { useHelp } from '../../context/HelpContext';

interface HelpIconButtonProps {
  topicId: string;
  title?: string;
  className?: string;
  variant?: string;
  size?: 'sm' | 'lg';
}

export const HelpIconButton: React.FC<HelpIconButtonProps> = ({
  topicId,
  title = 'View Help',
  className = '',
  variant = 'link',
  size
}) => {
  const { openHelp } = useHelp();

  return (
    <Button
      variant={variant}
      className={`p-0 align-items-center justify-content-center text-decoration-none ${className}`}
      onClick={(e) => {
        e.stopPropagation();
        openHelp(topicId);
      }}
      title={title}
      aria-label={title}
      style={{ lineHeight: 1 }}
    >
      <FontAwesomeIcon icon={faQuestionCircle} size={size} />
    </Button>
  );
};
