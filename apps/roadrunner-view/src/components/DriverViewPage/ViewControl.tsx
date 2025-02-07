import React, { useEffect, useRef } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowLeft, faArrowRight, fa0 } from '@fortawesome/free-solid-svg-icons';

const MS_FRAME_TIME = 50; // Interval for adjusting view (e.g., 50ms)

export const ViewControl = (props: {
  degViewOffset:number,
  setDegViewOffset:any
}) => {
  const isAdjustingLeft = useRef(false); // Left button state
  const isAdjustingRight = useRef(false); // Right button state

  // Effect to handle view adjustment when buttons are pressed
  useEffect(() => {
    let timerId: NodeJS.Timeout;

    const adjustView = () => {
      props.setDegViewOffset((prev:number) => {
        if (isAdjustingLeft.current) {
          return prev - 5; // Adjust left by 5 degrees
        } else if (isAdjustingRight.current) {
          return prev + 5; // Adjust right by 5 degrees
        }
        return prev;
      });

      // Continue the loop
      timerId = setTimeout(adjustView, MS_FRAME_TIME);
    };

    // Start the loop when either button is pressed
    if (isAdjustingLeft.current || isAdjustingRight.current) {
      timerId = setTimeout(adjustView, MS_FRAME_TIME);
    }

    // Cleanup: Stop the timer on unmount or when neither button is pressed
    return () => clearTimeout(timerId);
  }, [props, props.degViewOffset, props.setDegViewOffset]);

  const handleMouseDown = (direction: 'left' | 'right') => {
    if (direction === 'left') {
      isAdjustingLeft.current = true;
    } else if (direction === 'right') {
      isAdjustingRight.current = true;
    }
  };

  const handleMouseUp = () => {
    isAdjustingLeft.current = false;
    isAdjustingRight.current = false;
  };

  const handleCenter = () => {
    props.setDegViewOffset(0); // Reset to center
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'center', gap: '10px' }}>
        <button
          onMouseDown={() => handleMouseDown('left')}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp} // Handle edge case where mouse leaves button
          onTouchStart={() => handleMouseDown('left')}
          onTouchEnd={handleMouseUp}
          onTouchCancel={handleMouseUp}
        >
          <FontAwesomeIcon icon={ faArrowLeft } className="mr-3" />
        </button>
        <button
          onClick={handleCenter}
          onTouchStart={handleCenter}
        >
          <FontAwesomeIcon icon={ fa0 } className="mr-3" />
        </button>
        <button
          onMouseDown={() => handleMouseDown('right')}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
          onTouchStart={() => handleMouseDown('right')}
          onTouchEnd={handleMouseUp}
          onTouchCancel={handleMouseUp}
        >
          <FontAwesomeIcon icon={ faArrowRight } className="mr-3" />
        </button>
      </div>
      <div style={{ display: 'flex', justifyContent: 'center', fontSize: '1.1rem' }}>
        Offset: {props.degViewOffset}°
      </div>
    </div>
  );
}