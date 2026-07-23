import React from "react";
import { useNavigate } from "react-router-dom";

export const NavBarBrand: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div 
      className="nav-bar__brand" 
      onClick={() => navigate('/home')} 
      style={{ cursor: 'pointer' }}
    >
      <img
        className="nav-bar__logo"
        src="https://tarterware.com/wp-content/uploads/2024/04/cropped-Gemini_Generated_Image_xnjtxoxnjtxoxnjt.jpeg"
        alt="Tarterware"
        width="40"
        height="40"
      />
    </div>
  );
};
