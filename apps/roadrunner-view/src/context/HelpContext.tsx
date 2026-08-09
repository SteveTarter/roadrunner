import React, { createContext, useContext, useState, useCallback } from 'react';

interface HelpContextType {
  activeTopicId: string | null;
  isOpen: boolean;
  openHelp: (topicId: string) => void;
  closeHelp: () => void;
  toggleHelp: (topicId?: string) => void;
}

const HelpContext = createContext<HelpContextType>({
  activeTopicId: null,
  isOpen: false,
  openHelp: () => {},
  closeHelp: () => {},
  toggleHelp: () => {},
});

export const HelpProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [activeTopicId, setActiveTopicId] = useState<string | null>(null);
  const [isOpen, setIsOpen] = useState(false);

  const openHelp = useCallback((topicId: string) => {
    setActiveTopicId(topicId);
    setIsOpen(true);
  }, []);

  const closeHelp = useCallback(() => {
    setIsOpen(false);
  }, []);

  const toggleHelp = useCallback((topicId?: string) => {
    setIsOpen((prevOpen) => {
      if (!prevOpen) {
        if (topicId) setActiveTopicId(topicId);
        return true;
      }
      // If already open and topic matches, close it; if topic is different, switch topic
      if (topicId && topicId !== activeTopicId) {
        setActiveTopicId(topicId);
        return true;
      }
      return false;
    });
  }, [activeTopicId]);

  return (
    <HelpContext.Provider value={{ activeTopicId, isOpen, openHelp, closeHelp, toggleHelp }}>
      {children}
    </HelpContext.Provider>
  );
};

export const useHelp = () => useContext(HelpContext);
